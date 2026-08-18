package com.tubelite.app.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.tubelite.app.data.PlayableStream
import com.tubelite.app.data.QualityOption
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.download.DownloadHelper
import com.tubelite.app.playback.TubeMediaSourceFactory

private fun buildMediaItem(title: String, q: QualityOption): MediaItem {
    val bundle = Bundle()
    q.progressiveUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_PROGRESSIVE, it) }
    q.videoOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_VIDEO_ONLY, it) }
    q.audioOnlyUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_AUDIO_ONLY, it) }
    q.hlsUrl?.let { bundle.putString(TubeMediaSourceFactory.KEY_HLS, it) }
    val fallbackUri = q.progressiveUrl ?: q.hlsUrl ?: q.videoOnlyUrl ?: "about:blank"
    return MediaItem.Builder()
        .setUri(fallbackUri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(bundle).build())
        .build()
}

@Composable
fun PlayerScreen(
    video: VideoResult,
    controller: MediaController?,
    autoPlayEnabled: Boolean,
    isFullscreen: Boolean,
    onFullscreenChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var streamTitle by remember { mutableStateOf(video.title) }
    var qualities by remember { mutableStateOf<List<QualityOption>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<QualityOption?>(null) }
    var qualityMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(video.url, controller) {
        if (controller == null) return@LaunchedEffect
        loading = true
        error = null
        try {
            val playable: PlayableStream = YoutubeRepository.getPlayableStream(video.url)
            streamTitle = playable.title
            qualities = playable.options
            selectedQuality = playable.default

            controller.setMediaItem(buildMediaItem(playable.title, playable.default))
            controller.prepare()
            controller.playWhenReady = autoPlayEnabled
        } catch (e: Exception) {
            error = "স্ট্রিম লোড করা যায়নি: ${e.message}"
        } finally {
            loading = false
        }
    }

    // Fullscreen: hide system bars + lock landscape
    DisposableEffect(isFullscreen) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        onDispose {}
    }

    val videoHeight = if (isFullscreen) 0.dp else 220.dp

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (isFullscreen) Modifier.fillMaxSize() else Modifier.height(videoHeight))
        ) {
            if (controller != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = {
                        PlayerView(it).apply {
                            player = controller
                            useController = true
                        }
                    }
                )
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            IconButton(
                onClick = { onFullscreenChange(!isFullscreen) },
                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(6.dp)
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = androidx.compose.ui.graphics.Color.White
                )
            }
        }

        if (isFullscreen) return@Column

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
                val q = selectedQuality
                val url = q?.progressiveUrl ?: q?.videoOnlyUrl
                if (url != null) {
                    DownloadHelper.downloadVideo(context, url, streamTitle)
                    Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "ডাউনলোড লিংক পাওয়া যায়নি", Toast.LENGTH_SHORT).show()
                }
            }) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("ডাউনলোড")
            }

            Box {
                OutlinedButton(onClick = { qualityMenuOpen = true }) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(selectedQuality?.label ?: "কোয়ালিটি")
                }
                DropdownMenu(expanded = qualityMenuOpen, onDismissRequest = { qualityMenuOpen = false }) {
                    qualities.forEach { q ->
                        DropdownMenuItem(
                            text = { Text(q.label) },
                            onClick = {
                                qualityMenuOpen = false
                                if (controller != null && q != selectedQuality) {
                                    val pos = controller.currentPosition
                                    val wasPlaying = controller.isPlaying
                                    selectedQuality = q
                                    controller.setMediaItem(buildMediaItem(streamTitle, q))
                                    controller.prepare()
                                    controller.seekTo(pos)
                                    controller.playWhenReady = wasPlaying
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
