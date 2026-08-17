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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository
import com.tubelite.app.download.DownloadHelper
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    video: VideoResult,
    autoPlayEnabled: Boolean,
    onEnterPip: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var streamUrl by remember { mutableStateOf<String?>(null) }
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
            val playable = YoutubeRepository.getPlayableStream(video.url)
            streamUrl = playable.videoStreamUrl
            streamTitle = playable.title
            exoPlayer.setMediaItem(MediaItem.fromUri(playable.videoStreamUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = autoPlayEnabled
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
                val url = streamUrl
                if (url != null) {
                    DownloadHelper.downloadVideo(context, url, streamTitle)
                    Toast.makeText(context, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show()
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
