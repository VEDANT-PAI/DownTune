/*
 * DownTune (2026) - fork of ArchiveTune
 * ArchiveTune © Rukamori and contributors, GPL-3.0. This file is part of a modified version.
 * Pear Desktop (MIT, © pear-devs) inspired the plugin lifecycle design.
 */

package moe.rukamori.archivetune.plugins

import androidx.compose.runtime.Immutable

/** Static description of a plugin. */
@Immutable
data class PluginManifest(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "DownTune",
)

/** One user-editable setting of a plugin. The UI is generated from this schema. */
sealed interface PluginSetting {
    val key: String
    val label: String
    val description: String?

    data class Toggle(
        override val key: String,
        override val label: String,
        val default: Boolean,
        override val description: String? = null,
    ) : PluginSetting

    data class IntRange(
        override val key: String,
        override val label: String,
        val default: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
        override val description: String? = null,
    ) : PluginSetting

    data class Text(
        override val key: String,
        override val label: String,
        val default: String = "",
        val hint: String = "",
        override val description: String? = null,
    ) : PluginSetting
}

/** Events a plugin can observe. Delivered on the main thread: keep handlers fast. */
sealed interface PluginEvent {
    data class TrackChanged(val track: TrackInfo) : PluginEvent

    data class PlayingChanged(val isPlaying: Boolean, val track: TrackInfo?) : PluginEvent

    data class PlaybackEnded(val track: TrackInfo?) : PluginEvent

}

@Immutable
data class TrackInfo(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String?,
    /** Duration in ms, or null while unknown. */
    val durationMs: Long?,
    val artworkUrl: String?,
)

/** Limited, thread-safe handle on the player. All calls are posted to the main thread. */
interface PluginPlayerController {
    fun play()

    fun pause()

    fun skipToNext()

    fun skipToPrevious()

    fun seekTo(positionMs: Long)
}

/** Read access to this plugin's own settings. Values are always valid (defaults applied). */
interface PluginSettingsReader {
    fun bool(key: String): Boolean

    fun int(key: String): Int

    fun string(key: String): String
}

interface PluginContext {
    val settings: PluginSettingsReader
    val player: PluginPlayerController
    val log: (String) -> Unit
}

/**
 * A DownTune plugin. Mirrors Pear's lifecycle: [onEnable] ~ start, [onDisable] ~ stop,
 * [onSettingsChanged] ~ onConfigChange. Plugins are off by default.
 */
interface DownTunePlugin {
    val manifest: PluginManifest
    val settings: List<PluginSetting> get() = emptyList()

    fun onEnable(context: PluginContext) {}

    fun onDisable() {}

    fun onSettingsChanged(context: PluginContext) {}

    fun onEvent(
        event: PluginEvent,
        context: PluginContext,
    ) {}
}
