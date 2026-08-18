package com.tubelite.app.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.tubelite.app.data.PlayableStream
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.download.DownloadHelper

private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Mobile Safari/537.36"

@Composable
fun PlayerScreen(
    video: VideoResult,
    autoPlayEnabled: Boolean,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var streamTitle by remember { mutableStateOf(video.title) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = autoPlayEnabled
        }
    }

    LaunchedEffect(video.url) {
        loading = true
        error = null
        try {
            val playable: PlayableStream = YoutubeRepository.getPlayableStream(video.url)
            streamTitle = playable.title
            downloadUrl = playable.progressiveUrl ?: playable.videoOnlyUrl

            val dataSourceFactory = DefaultHttpDataSource.Factory().setUserAgent(UA)

            val mediaSource = when {
                playable.progressiveUrl != null ->
                    ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(playable.progressiveUrl))

                playable.videoOnlyUrl != null && playable.audioOnlyUrl != null ->
                    MergingMediaSource(
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(playable.videoOnlyUrl)),
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(playable.audioOnlyUrl))
                    )

                playable.hlsUrl != null ->
                    HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(playable.hlsUrl))

                else -> null
            }

            if (mediaSource != null) {
                exoPlayer.setMediaSource(mediaSource)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = autoPlayEnabled
            } else {
                error = "কোনো প্লে-যোগ্য স্ট্রিম পাওয়া যায়নি"
            }
        } catch (e: Exception) {
            error = "স্ট্রিম লোড করা যায়নি: ${e.message}"
        } finally {
            loading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = true
                    }
                }
            )
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }

        Text(
            streamTitle,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = {
                val url = downloadUrl
                if (url != null) {
                    DownloadHelper.downloadVideo(context, url, streamTitle)
                    Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "এই ভিডিওর জন্য ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("ডাউনলোড")
            }

            OutlinedButton(onClick = {
                exoPlayer.playWhenReady = true
                onEnterPip()
                activity?.enterPictureInPictureMode(
                    android.app.PictureInPictureParams.Builder().build()
                )
            }) {
                Icon(Icons.Default.PictureInPictureAlt, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("PiP")
            }
        }
    }
}
