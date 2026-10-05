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

/** Skips tracks shorter than a configurable length (intros, skits, ads in playlists). */
class SkipShortTracksPlugin : DownTunePlugin {
    override val manifest =
        PluginManifest(
            id = "skip-short-tracks",
            name = "Skip short tracks",
            description = "Automatically skips tracks shorter than the length you choose.",
        )

    override val settings =
        listOf(
            PluginSetting.IntRange(
                key = "min_seconds",
                label = "Minimum length",
                default = 30,
                min = 5,
                max = 180,
                unit = "s",
                description = "Tracks shorter than this are skipped.",
            ),
        )

    override fun onEvent(
        event: PluginEvent,
        context: PluginContext,
    ) {
        if (event !is PluginEvent.TrackChanged) return
        val duration = event.track.durationMs ?: return // unknown length: leave it alone
        if (duration < context.settings.int("min_seconds") * 1000L) {
            context.log("skipping '${event.track.title}' (${duration / 1000}s)")
            context.player.skipToNext()
        }
    }
}
