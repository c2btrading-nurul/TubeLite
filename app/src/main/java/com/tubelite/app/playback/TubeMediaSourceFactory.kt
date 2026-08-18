package com.tubelite.app.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Reads video/audio URLs stashed in a MediaItem's request-metadata extras
 * (set by PlayerScreen) and builds the right MediaSource — progressive,
 * merged video-only+audio-only DASH tracks, or HLS.
 */
@UnstableApi
class TubeMediaSourceFactory(
    private val dataSourceFactory: DataSource.Factory
) : MediaSource.Factory {

    override fun setDrmSessionManagerProvider(p: DrmSessionManagerProvider): MediaSource.Factory = this
    override fun setLoadErrorHandlingPolicy(p: LoadErrorHandlingPolicy): MediaSource.Factory = this
    override fun getSupportedTypes(): IntArray = intArrayOf(C.CONTENT_TYPE_OTHER, C.CONTENT_TYPE_HLS)

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val extras = mediaItem.requestMetadata.extras
        val progressive = extras?.getString(KEY_PROGRESSIVE)
        val videoOnly = extras?.getString(KEY_VIDEO_ONLY)
        val audioOnly = extras?.getString(KEY_AUDIO_ONLY)
        val hls = extras?.getString(KEY_HLS)

        return when {
            progressive != null -> ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(progressive)))

            videoOnly != null && audioOnly != null -> MergingMediaSource(
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(Uri.parse(videoOnly))),
                ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(Uri.parse(audioOnly)))
            )

            hls != null -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(Uri.parse(hls)))

            else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
    }

    companion object {
        const val KEY_PROGRESSIVE = "tubelite_progressive_url"
        const val KEY_VIDEO_ONLY = "tubelite_video_only_url"
        const val KEY_AUDIO_ONLY = "tubelite_audio_only_url"
        const val KEY_HLS = "tubelite_hls_url"
    }
}
