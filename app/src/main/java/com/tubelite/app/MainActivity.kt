package com.tubelite.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tubelite.app.data.AppSettingsStore
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.service.PlaybackService
import com.tubelite.app.ui.screens.HistoryScreen
import com.tubelite.app.ui.screens.HomeScreen
import com.tubelite.app.ui.screens.MiniPlayerBar
import com.tubelite.app.ui.screens.PlayerScreen
import com.tubelite.app.ui.screens.SavedScreen
import com.tubelite.app.ui.screens.SearchScreen
import com.tubelite.app.ui.screens.SettingsScreen
import com.tubelite.app.ui.theme.TubeLiteTheme

private enum class BottomTab { HOME, HISTORY, SAVED, SETTINGS }

private val TOP_BAR_HEIGHT = 52.dp
private val BOTTOM_BAR_HEIGHT = 56.dp

class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkMode by remember { mutableStateOf(AppSettingsStore.isDarkMode(this)) }
            TubeLiteTheme(darkTheme = darkMode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(
                        darkMode = darkMode,
                        onDarkModeChange = {
                            darkMode = it
                            AppSettingsStore.setDarkMode(this, it)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
private fun AppRoot(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var nowPlaying by remember { mutableStateOf<VideoResult?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var autoPlay by remember { mutableStateOf(AppSettingsStore.isAutoplayNextDefault(context)) }
    var showSearch by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var preparedUrl by remember { mutableStateOf<String?>(null) }
    var playHistory by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var queue by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var queueIndex by remember { mutableStateOf(-1) }
    var currentTab by remember { mutableStateOf(BottomTab.HOME) }

    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(Unit) {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            val c = controllerFuture.get()
            controller = c
            if (nowPlaying == null && c.mediaItemCount > 0) {
                val restored = NowPlayingStore.load(context)
                if (restored != null) {
                    nowPlaying = restored
                    preparedUrl = restored.url
                    playerExpanded = false
                }
            }
        }, MoreExecutors.directExecutor())
        onDispose { MediaController.releaseFuture(controllerFuture) }
    }

    fun goHome() {
        currentTab = BottomTab.HOME
        showSearch = false
        playerExpanded = false
        isFullscreen = false
    }

    fun openSearch() {
        playerExpanded = false
        isFullscreen = false
        showSearch = true
    }

    fun playVideo(v: VideoResult, addToHistory: Boolean = true) {
        val current = nowPlaying
        if (addToHistory && current != null && current.url != v.url) {
            playHistory = playHistory + current
        }
        if (v.url != nowPlaying?.url) preparedUrl = null
        nowPlaying = v
        playerExpanded = true
        showSearch = false
    }

    fun playFromQueue(list: List<VideoResult>, index: Int) {
        queue = list
        queueIndex = index
        playVideo(list[index])
    }

    fun playPrevious() {
        val prev = playHistory.lastOrNull() ?: return
        playHistory = playHistory.dropLast(1)
        playVideo(prev, addToHistory = false)
    }

    fun playNextInQueue() {
        if (queueIndex in 0 until queue.lastIndex) {
            queueIndex += 1
            playVideo(queue[queueIndex], addToHistory = true)
        }
    }

    BackHandler(enabled = true) {
        when {
            isFullscreen -> isFullscreen = false
            playerExpanded -> playerExpanded = false
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
                TextButton(onClick = { showExitConfirm = false; activity?.finish() }) { Text("ব্যাকগ্রাউন্ডে চালু রাখুন") }
            },
            dismissButton = {
                TextButton(onClick = {
                    controller?.stop()
                    nowPlaying = null
                    preparedUrl = null
                    NowPlayingStore.clear(context)
                    showExitConfirm = false
                    activity?.finish()
                }) { Text("বন্ধ করুন") }
            }
        )
    }

    val video = nowPlaying

    Box(Modifier.fillMaxSize()) {
        // ---- মূল কনটেন্ট (বার-গুলোর জন্য প্যাডিং রেখে, তাই স্ক্রল করার সময় সত্যিকারের glass effect দেখা যাবে) ----
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = if (isFullscreen) 0.dp else TOP_BAR_HEIGHT,
                    bottom = if (isFullscreen) 0.dp else BOTTOM_BAR_HEIGHT
                )
        ) {
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
                    hasNext = queueIndex in 0 until queue.lastIndex,
                    onNext = { playNextInQueue() },
                    queueNextVideo = queue.getOrNull(queueIndex + 1),
                    onFullscreenChange = { isFullscreen = it },
                    onRelatedSelected = { playVideo(it) }
                )
            } else if (!isFullscreen) {
                when {
                    showSearch -> SearchScreen(onVideoSelected = { playVideo(it) })
                    currentTab == BottomTab.HOME -> HomeScreen(onVideoSelected = { playVideo(it) })
                    currentTab == BottomTab.HISTORY -> HistoryScreen(onVideoSelected = { playVideo(it) })
                    currentTab == BottomTab.SAVED -> SavedScreen(onPlayPlaylist = { list, idx -> playFromQueue(list, idx) })
                    currentTab == BottomTab.SETTINGS -> SettingsScreen(
                        darkMode = darkMode,
                        onDarkModeChange = onDarkModeChange,
                        autoplayNext = autoPlay,
                        onAutoplayNextChange = {
                            autoPlay = it
                            AppSettingsStore.setAutoplayNextDefault(context, it)
                        }
                    )
                }
            }
        }

        // ---- টপ বার (ভাসমান, গ্লাসি) ----
        if (!isFullscreen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT)
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSearch) {
                    IconButton(onClick = { showSearch = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
                val title = when {
                    showSearch -> "সার্চ"
                    currentTab == BottomTab.HOME -> "TubeLite"
                    currentTab == BottomTab.HISTORY -> "হিস্ট্রি"
                    currentTab == BottomTab.SAVED -> "সেভ করা"
                    else -> "সেটিংস"
                }
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { goHome() }
                        .padding(start = if (showSearch) 4.dp else 12.dp)
                )
                if (!showSearch) {
                    IconButton(onClick = { openSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
            }
        }

        // ---- মিনি-প্লেয়ার (বটম বারের ঠিক উপরে) ----
        if (!isFullscreen && video != null && !playerExpanded) {
            MiniPlayerBar(
                video = video,
                controller = controller,
                onExpand = { playerExpanded = true },
                onClose = {
                    controller?.stop()
                    nowPlaying = null
                    preparedUrl = null
                    NowPlayingStore.clear(context)
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BOTTOM_BAR_HEIGHT)
            )
        }

        // ---- বটম বার (সবসময় দৃশ্যমান, ভাসমান, গ্লাসি, কমপ্যাক্ট) ----
        if (!isFullscreen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BOTTOM_BAR_HEIGHT)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarItem(Icons.Default.Home, "হোম", currentTab == BottomTab.HOME && !showSearch) {
                    currentTab = BottomTab.HOME; showSearch = false; playerExpanded = false
                }
                BottomBarItem(Icons.Default.History, "হিস্ট্রি", currentTab == BottomTab.HISTORY && !showSearch) {
                    currentTab = BottomTab.HISTORY; showSearch = false; playerExpanded = false
                }
                BottomBarItem(Icons.Default.PlaylistPlay, "সেভ", currentTab == BottomTab.SAVED && !showSearch) {
                    currentTab = BottomTab.SAVED; showSearch = false; playerExpanded = false
                }
                BottomBarItem(Icons.Default.Settings, "সেটিংস", currentTab == BottomTab.SETTINGS && !showSearch) {
                    currentTab = BottomTab.SETTINGS; showSearch = false; playerExpanded = false
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    Column(
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}
