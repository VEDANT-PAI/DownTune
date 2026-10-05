/*
 * DownTune (2026) - fork of ArchiveTune
 * ArchiveTune © Rukamori and contributors, GPL-3.0. This file is part of a modified version.
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.ExoDownloadService
import moe.rukamori.archivetune.utils.isLocalMediaId

/**
 * One-tap offline download for the song that is playing. Uses the same queue as the song menu's
 * Download item: tap to download, tap again to cancel or remove. Hidden for songs imported from local files.
 */
@Composable
fun PlayerDownloadButton(
    mediaId: String,
    title: String,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = CircleShape,
) {
    if (mediaId.isBlank() || mediaId.isLocalMediaId()) return

    val context = LocalContext.current
    val download by LocalDownloadUtil.current
        .getDownload(mediaId)
        .collectAsState(initial = null)
    val state = download?.state
    val inProgress =
        state == Download.STATE_DOWNLOADING ||
            state == Download.STATE_QUEUED ||
            state == Download.STATE_RESTARTING
    val done = state == Download.STATE_COMPLETED

    val label =
        when {
            done -> R.string.remove_download
            inProgress -> R.string.downloading
            else -> R.string.action_download
        }

    Surface(
        onClick = {
            if (state == null || state == Download.STATE_FAILED) {
                val request =
                    DownloadRequest
                        .Builder(mediaId, mediaId.toUri())
                        .setCustomCacheKey(mediaId)
                        .setData(title.toByteArray())
                        .build()
                DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
            } else {
                DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, mediaId, false)
            }
        },
        shape = shape,
        color = containerColor,
        modifier = modifier.size(size),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
            Icon(
                painter = painterResource(if (done) R.drawable.offline else R.drawable.download),
                contentDescription = stringResource(label),
                tint = tint,
                modifier = Modifier.size(iconSize),
            )
            if (inProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(size - 8.dp),
                    color = tint,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}
