package com.tubelite.app.service

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.tubelite.app.playback.TubeMediaSourceFactory

private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(UA)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(TubeMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // ⚠️ মূল ফিক্স: ডিফল্ট আচরণে কন্ট্রোলার থেকে আসা MediaItem-এর
                // mediaId/extras (কোয়ালিটি URL, subtitle ইত্যাদি) sanitize হয়ে যায়।
                // এখানে item-টা অবিকৃত অবস্থায় ফেরত দেওয়া হচ্ছে, যাতে mediaId
                // (আসল YouTube URL) এবং আমাদের কাস্টম extras ঠিকভাবে player পর্যন্ত পৌঁছায়।
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>
                ): ListenableFuture<MutableList<MediaItem>> {
                    return Futures.immediateFuture(mediaItems)
                }
            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
