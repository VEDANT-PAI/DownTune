/*
 * DownTune (2026) - fork of ArchiveTune
 * ArchiveTune © Rukamori and contributors, GPL-3.0. This file is part of a modified version.
 * Pear Desktop (MIT, © pear-devs) inspired the plugin lifecycle design.
 */

package moe.rukamori.archivetune.plugins

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.plugins.builtin.NowPlayingWebhookPlugin
import moe.rukamori.archivetune.plugins.builtin.SkipShortTracksPlugin
import moe.rukamori.archivetune.utils.dataStore
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
data class PluginSettingValue(
    val schema: PluginSetting,
    val value: String,
)

@Immutable
data class PluginUiState(
    val manifest: PluginManifest,
    val enabled: Boolean,
    val settings: List<PluginSettingValue>,
    /** Set when the manager switched the plugin off after repeated errors. */
    val disabledByError: Boolean = false,
)

/**
 * Registry and lifecycle owner for plugins. Everything here runs on the main thread, which is
 * also where [MusicService][moe.rukamori.archivetune.playback.MusicService] delivers player callbacks.
 * A plugin that throws [MAX_FAILURES] times in a row is disabled so it cannot break playback.
 */
@Singleton
class PluginManager
    @Inject
    constructor(
        @ApplicationContext private val appContext: Context,
    ) {
        private val plugins: List<DownTunePlugin> =
            listOf(
                SkipShortTracksPlugin(),
                NowPlayingWebhookPlugin(),
            )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val mainHandler = Handler(Looper.getMainLooper())

        private val _state = MutableStateFlow<List<PluginUiState>>(emptyList())
        val state: StateFlow<List<PluginUiState>> = _state

        private var snapshot: Preferences? = null
        private val running = mutableSetOf<String>()
        private val failures = mutableMapOf<String, Int>()
        private val erroredIds = mutableSetOf<String>()
        private val lastSettings = mutableMapOf<String, List<String>>()

        @Volatile private var controller: PluginPlayerController = NoOpController

        init {
            scope.launch {
                appContext.dataStore.data.collect { prefs ->
                    snapshot = prefs
                    reconcile(prefs)
                }
            }
        }

        fun attachPlayer(playerController: PluginPlayerController) {
            controller = playerController
        }

        fun detachPlayer() {
            controller = NoOpController
        }

        fun setEnabled(
            id: String,
            enabled: Boolean,
        ) {
            if (plugins.none { it.manifest.id == id }) return
            if (enabled) erroredIds.remove(id)
            scope.launch { appContext.dataStore.edit { it[enabledKey(id)] = enabled } }
        }

        fun setSetting(
            id: String,
            key: String,
            value: String,
        ) {
            val plugin = plugins.firstOrNull { it.manifest.id == id } ?: return
            val schema = plugin.settings.firstOrNull { it.key == key } ?: return
            scope.launch { appContext.dataStore.edit { it[settingKey(id, key)] = sanitize(schema, value) } }
        }

        fun dispatch(event: PluginEvent) {
            if (running.isEmpty()) return
            for (plugin in plugins) {
                val id = plugin.manifest.id
                if (id !in running) continue
                guard(plugin) { plugin.onEvent(event, contextFor(plugin)) }
            }
        }

        // region internals

        private fun reconcile(prefs: Preferences) {
            for (plugin in plugins) {
                val id = plugin.manifest.id
                val wanted = prefs[enabledKey(id)] == true
                val isRunning = id in running
                when {
                    wanted && !isRunning -> {
                        running += id
                        failures[id] = 0
                        lastSettings[id] = settingsSignature(plugin, prefs)
                        guard(plugin) { plugin.onEnable(contextFor(plugin)) }
                    }

                    !wanted && isRunning -> {
                        running -= id
                        runCatching { plugin.onDisable() }.onFailure { Timber.w(it, "plugin %s: onDisable failed", id) }
                    }

                    wanted && isRunning -> {
                        // The datastore is shared with the whole app; only react to this plugin's own keys.
                        val signature = settingsSignature(plugin, prefs)
                        if (signature != lastSettings[id]) {
                            lastSettings[id] = signature
                            guard(plugin) { plugin.onSettingsChanged(contextFor(plugin)) }
                        }
                    }
                }
            }
            _state.value = plugins.map { it.toUiState(prefs) }
        }

        private fun settingsSignature(
            plugin: DownTunePlugin,
            prefs: Preferences,
        ): List<String> =
            plugin.settings.map { prefs[settingKey(plugin.manifest.id, it.key)] ?: defaultOf(it) }

        private fun guard(
            plugin: DownTunePlugin,
            block: () -> Unit,
        ) {
            val id = plugin.manifest.id
            try {
                block()
                failures[id] = 0
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                val count = (failures[id] ?: 0) + 1
                failures[id] = count
                Timber.w(t, "plugin %s failed (%d/%d)", id, count, MAX_FAILURES)
                if (count >= MAX_FAILURES) {
                    erroredIds += id
                    running -= id
                    runCatching { plugin.onDisable() }
                    scope.launch { appContext.dataStore.edit { it[enabledKey(id)] = false } }
                }
            }
        }

        private fun contextFor(plugin: DownTunePlugin): PluginContext =
            object : PluginContext {
                override val settings: PluginSettingsReader = reader(plugin)
                override val player: PluginPlayerController = DelegatingController()
                override val log: (String) -> Unit = { Timber.tag("Plugin/${plugin.manifest.id}").d(it) }
            }

        private fun reader(plugin: DownTunePlugin) =
            object : PluginSettingsReader {
                private fun raw(key: String): String? {
                    val schema = plugin.settings.firstOrNull { it.key == key } ?: return null
                    return snapshot?.get(settingKey(plugin.manifest.id, key)) ?: defaultOf(schema)
                }

                override fun bool(key: String) = raw(key)?.toBooleanStrictOrNull() ?: false

                override fun int(key: String) = raw(key)?.toIntOrNull() ?: 0

                override fun string(key: String) = raw(key).orEmpty()
            }

        private fun DownTunePlugin.toUiState(prefs: Preferences) =
            PluginUiState(
                manifest = manifest,
                enabled = prefs[enabledKey(manifest.id)] == true,
                settings =
                    settings.map { s ->
                        PluginSettingValue(s, prefs[settingKey(manifest.id, s.key)] ?: defaultOf(s))
                    },
                disabledByError = manifest.id in erroredIds,
            )

        /** Always posts to the main thread; the controller is looked up at call time. */
        private inner class DelegatingController : PluginPlayerController {
            private fun post(block: PluginPlayerController.() -> Unit) {
                mainHandler.post { runCatching { controller.block() } }
            }

            override fun play() = post { play() }

            override fun pause() = post { pause() }

            override fun skipToNext() = post { skipToNext() }

            override fun skipToPrevious() = post { skipToPrevious() }

            override fun seekTo(positionMs: Long) = post { seekTo(positionMs) }
        }

        private object NoOpController : PluginPlayerController {
            override fun play() = Unit

            override fun pause() = Unit

            override fun skipToNext() = Unit

            override fun skipToPrevious() = Unit

            override fun seekTo(positionMs: Long) = Unit
        }

        // endregion

        private companion object {
            const val MAX_FAILURES = 3

            fun enabledKey(id: String) = booleanPreferencesKey("plugin.$id.enabled")

            fun settingKey(
                id: String,
                key: String,
            ) = stringPreferencesKey("plugin.$id.s.$key")

            fun defaultOf(schema: PluginSetting): String =
                when (schema) {
                    is PluginSetting.Toggle -> schema.default.toString()
                    is PluginSetting.IntRange -> schema.default.toString()
                    is PluginSetting.Text -> schema.default
                }

            fun sanitize(
                schema: PluginSetting,
                value: String,
            ): String =
                when (schema) {
                    is PluginSetting.Toggle -> (value.toBooleanStrictOrNull() ?: schema.default).toString()
                    is PluginSetting.IntRange ->
                        (value.toIntOrNull() ?: schema.default).coerceIn(schema.min, schema.max).toString()
                    is PluginSetting.Text -> value.take(MAX_TEXT_LENGTH)
                }

            const val MAX_TEXT_LENGTH = 512
        }
    }
