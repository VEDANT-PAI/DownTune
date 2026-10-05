/*
 * DownTune (2026) - fork of ArchiveTune
 * ArchiveTune © Rukamori and contributors, GPL-3.0. This file is part of a modified version.
 */

package moe.rukamori.archivetune.plugins.builtin

import moe.rukamori.archivetune.plugins.DownTunePlugin
import moe.rukamori.archivetune.plugins.PluginContext
import moe.rukamori.archivetune.plugins.PluginEvent
import moe.rukamori.archivetune.plugins.PluginManifest
import moe.rukamori.archivetune.plugins.PluginSetting
import moe.rukamori.archivetune.plugins.TrackInfo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends the current track to a URL you control (stream overlays, home automation, bots).
 * Off by default; nothing is sent until you enable it AND set a URL.
 */
class NowPlayingWebhookPlugin : DownTunePlugin {
    override val manifest =
        PluginManifest(
            id = "now-playing-webhook",
            name = "Now playing webhook",
            description = "POSTs track and play/pause info as JSON to a URL you set.",
        )

    override val settings =
        listOf(
            PluginSetting.Text(
                key = "url",
                label = "Webhook URL",
                hint = "https://example.com/hook",
                description = "Only http(s) URLs are used. Song title, artist and album are sent to this address.",
            ),
            PluginSetting.Toggle(
                key = "send_play_state",
                label = "Also send play/pause",
                default = true,
            ),
        )

    private val client by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    override fun onEvent(
        event: PluginEvent,
        context: PluginContext,
    ) {
        when (event) {
            is PluginEvent.TrackChanged -> send(context, "track_changed", event.track, null)
            is PluginEvent.PlayingChanged ->
                if (context.settings.bool("send_play_state")) {
                    send(context, if (event.isPlaying) "playing" else "paused", event.track, event.isPlaying)
                }
            is PluginEvent.PlaybackEnded -> send(context, "ended", event.track, false)
        }
    }

    private fun send(
        context: PluginContext,
        type: String,
        track: TrackInfo?,
        isPlaying: Boolean?,
    ) {
        val url = context.settings.string("url").trim()
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return

        val body =
            JSONObject()
                .put("event", type)
                .apply {
                    isPlaying?.let { put("isPlaying", it) }
                    track?.let {
                        put("mediaId", it.mediaId)
                        put("title", it.title)
                        put("artist", it.artist)
                        put("album", it.album ?: JSONObject.NULL)
                        put("durationMs", it.durationMs ?: JSONObject.NULL)
                        put("artworkUrl", it.artworkUrl ?: JSONObject.NULL)
                    }
                }.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request =
            try {
                Request.Builder().url(url).post(body).build()
            } catch (_: IllegalArgumentException) {
                context.log("invalid webhook URL")
                return
            }

        // enqueue() runs on OkHttp's dispatcher, so the main thread is never blocked.
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) = context.log("webhook failed: ${e.message}")

                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    response.close()
                }
            },
        )
    }
}
