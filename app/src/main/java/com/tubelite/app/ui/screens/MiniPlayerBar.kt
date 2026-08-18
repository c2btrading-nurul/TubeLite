package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.tubelite.app.data.VideoResult

/** নিচে একটা ছোট বার — YouTube-এর মিনি-প্লেয়ারের মতো, ব্যাকগ্রাউন্ডে চলা ভিডিওতে ফিরে যাওয়ার জন্য */
@Composable
fun MiniPlayerBar(
    video: VideoResult,
    controller: MediaController?,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(controller?.isPlaying == true) }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        controller?.addListener(listener)
        onDispose { controller?.removeListener(listener) }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onExpand),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.width(90.dp).fillMaxHeight()
        )
        Spacer(Modifier.width(10.dp))
        Text(
            video.title,
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = {
            if (isPlaying) controller?.pause() else controller?.play()
        }) {
            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close")
        }
    }
}
