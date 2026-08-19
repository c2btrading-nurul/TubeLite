package com.tubelite.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tubelite.app.data.VideoResult
import com.tubelite.app.service.PlaybackService
import com.tubelite.app.ui.screens.HomeScreen
import com.tubelite.app.ui.screens.MiniPlayerBar
import com.tubelite.app.ui.screens.PlayerScreen
import com.tubelite.app.ui.screens.SearchScreen
import com.tubelite.app.ui.theme.TubeLiteTheme

class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TubeLiteTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // এই state-গুলো নিচের controller-connect effect-এ ব্যবহার হয়, তাই আগে ডিক্লেয়ার করা হচ্ছে
    var nowPlaying by remember { mutableStateOf<VideoResult?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var autoPlay by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var preparedUrl by remember { mutableStateOf<String?>(null) }
    var playHistory by remember { mutableStateOf<List<VideoResult>>(emptyList()) }

    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val c = controllerFuture.get()
            controller = c

            if (nowPlaying == null && c.mediaItemCount > 0) {
                val restored = com.tubelite.app.data.NowPlayingStore.load(context)
                if (restored != null) {
                    nowPlaying = restored
                    preparedUrl = restored.url
                    playerExpanded = false
                }
            }
        }, MoreExecutors.directExecutor())
        onDispose {
            MediaController.releaseFuture(controllerFuture)
        }
    }

    fun playVideo(v: VideoResult, addToHistory: Boolean = true) {
        val current = nowPlaying
        if (addToHistory && current != null && current.url != v.url) {
            playHistory = playHistory + current // পরে "Previous" বাটনে এখানে ফিরে আসার জন্য
        }
        if (v.url != nowPlaying?.url) {
            preparedUrl = null // নতুন ভিডিও — PlayerScreen-কে reload করতে বলা হচ্ছে
        }
        nowPlaying = v
        playerExpanded = true
        showSearch = false
    }

    fun playPrevious() {
        val prev = playHistory.lastOrNull() ?: return
        playHistory = playHistory.dropLast(1)
        playVideo(prev, addToHistory = false) // history-তে আবার যোগ করার দরকার নেই
    }

    BackHandler(enabled = true) {
        when {
            isFullscreen -> isFullscreen = false
            playerExpanded -> playerExpanded = false // collapse to mini-player, keep playing
            showSearch -> showSearch = false
            controller?.isPlaying == true -> showExitConfirm = true
            else -> activity?.finish()
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("ব্যাকগ্রাউন্ডে চালু রাখবেন?") },
            text = { Text("ভিডিও/অডিও প্লে হচ্ছে। অ্যাপ বন্ধ করার পরও এটা চালু রাখতে চান?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    activity?.finish()
                }) { Text("ব্যাকগ্রাউন্ডে চালু রাখুন") }
            },
            dismissButton = {
                TextButton(onClick = {
                    controller?.stop()
                    nowPlaying = null
                    preparedUrl = null
                    com.tubelite.app.data.NowPlayingStore.clear(context)
                    showExitConfirm = false
                    activity?.finish()
                }) { Text("বন্ধ করুন") }
            }
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (!isFullscreen) {
            TopAppBar(
                title = {
                    if (showSearch) Text("সার্চ") else Text("TubeLite", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    if (showSearch) {
                        IconButton(onClick = { showSearch = false }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showSearch) {
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Auto", modifier = Modifier.padding(end = 2.dp), style = MaterialTheme.typography.labelSmall)
                            Switch(checked = autoPlay, onCheckedChange = { autoPlay = it })
                        }
                    }
                }
            )
        }

        val video = nowPlaying
        if (playerExpanded && video != null) {
            PlayerScreen(
                video = video,
                controller = controller,
                autoPlayEnabled = autoPlay,
                isFullscreen = isFullscreen,
                alreadyPrepared = preparedUrl == video.url,
                onPrepared = { preparedUrl = it },
                hasPrevious = playHistory.isNotEmpty(),
                onPrevious = { playPrevious() },
                onFullscreenChange = { isFullscreen = it },
                onRelatedSelected = { playVideo(it) }
            )
            if (!isFullscreen) {
                TextButton(onClick = { playerExpanded = false }) { Text("← হোমে যান (প্লে চলবে)") }
            }
        }

        if (!isFullscreen && !playerExpanded) {
            Box(Modifier.fillMaxSize().weight(1f)) {
                if (showSearch) {
                    SearchScreen(onVideoSelected = { playVideo(it) })
                } else {
                    HomeScreen(onVideoSelected = { playVideo(it) })
                }
            }

            if (video != null) {
                MiniPlayerBar(
                    video = video,
                    controller = controller,
                    onExpand = { playerExpanded = true },
                    onClose = {
                        controller?.stop()
                        nowPlaying = null
                        preparedUrl = null
                        com.tubelite.app.data.NowPlayingStore.clear(context)
                    }
                )
            }
        }
    }
}
