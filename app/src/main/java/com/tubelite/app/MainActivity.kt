package com.tubelite.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.tubelite.app.data.AppSettingsStore
import com.tubelite.app.data.AppLanguageStore
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.service.PlaybackService
import com.tubelite.app.ui.screens.ChannelScreen
import com.tubelite.app.ui.screens.HistoryScreen
import com.tubelite.app.ui.screens.HomeScreen
import com.tubelite.app.ui.screens.MiniPlayerBar
import com.tubelite.app.ui.screens.PlayerScreen
import com.tubelite.app.ui.screens.ProfileScreen
import com.tubelite.app.ui.screens.SavedScreen
import com.tubelite.app.ui.screens.SubscriptionScreen
import com.tubelite.app.ui.screens.ShortsScreen
import com.tubelite.app.ui.screens.InlineSearchPanel
import com.tubelite.app.ui.screens.LocalAppLanguage
import com.tubelite.app.ui.theme.TubeLiteTheme

private enum class BottomTab { HOME, HISTORY, SUBSCRIPTIONS, SHORTS, SAVED, PROFILE }

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
    var shortsEnabled by remember { mutableStateOf(AppSettingsStore.isShortsEnabled(context)) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchRequestToken by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var shortsFullscreen by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var preparedUrl by remember { mutableStateOf<String?>(null) }
    var playHistory by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var queue by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var queueIndex by remember { mutableStateOf(-1) }
    var currentTab by remember { mutableStateOf(BottomTab.HOME) }
    var channelUrl by remember { mutableStateOf<String?>(null) }
    
    var appLanguage by remember { mutableStateOf(AppLanguageStore.get(context)) }
    
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
        channelUrl = null
    }

    fun openSearch() {
        playerExpanded = false
        isFullscreen = false
        channelUrl = null
        showSearch = true
    }

    fun playVideo(v: VideoResult, addToHistory: Boolean = true, clearQueue: Boolean = true) {
        val current = nowPlaying
        if (addToHistory && current != null && current.url != v.url) {
            playHistory = playHistory + current
        }
        if (v.url != nowPlaying?.url) preparedUrl = null
        if (clearQueue) {
            queue = emptyList()
            queueIndex = -1
        }
        nowPlaying = v
        playerExpanded = true
        showSearch = false
        channelUrl = null
    }

    fun playFromQueue(list: List<VideoResult>, index: Int) {
        if (list.isEmpty()) return
        if (index !in list.indices) return
    
        queue = list
        queueIndex = index
    
        // Playlist navigation-কে সাধারণ watch history থেকে আলাদা রাখি।
        playVideo(
            v = list[index],
            addToHistory = false,
            clearQueue = false
        )
    }
    
    fun playQueuePrevious() {
        if (queue.isEmpty()) return
    
        val previousIndex = queueIndex - 1
    
        if (previousIndex in queue.indices) {
            playFromQueue(queue, previousIndex)
        }
    }
    
    fun playQueueNext() {
        if (queue.isEmpty()) return
    
        val nextIndex = queueIndex + 1
    
        if (nextIndex in queue.indices) {
            playFromQueue(queue, nextIndex)
        }
    }
    
    fun playPrevious() {
        // Playlist-এর ভিতরে থাকলে আগে Playlist Previous কাজ করবে।
        if (queue.isNotEmpty() && queueIndex > 0) {
            playQueuePrevious()
            return
        }
    
        // Playlist না থাকলে সাধারণ ভিডিও history-এর Previous।
        val prev = playHistory.lastOrNull() ?: return
    
        playHistory = playHistory.dropLast(1)
    
        playVideo(
            v = prev,
            addToHistory = false,
            clearQueue = true
        )
    }

    BackHandler(enabled = true) {
        when {
            shortsFullscreen -> shortsFullscreen = false
            isFullscreen -> isFullscreen = false
            currentTab != BottomTab.HOME || showSearch || channelUrl != null || playerExpanded -> {
                // Home ছাড়া যেকোনো জায়গা থেকে Back = প্রথমে Home।
                goHome()
            }
            controller?.isPlaying == true -> showExitConfirm = true
            else -> activity?.finish()
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(if (appLanguage == AppLanguageStore.ENGLISH) "Keep playing in background?" else "ব্যাকগ্রাউন্ডে চালু রাখবেন?") },
            text = { Text(if (appLanguage == AppLanguageStore.ENGLISH) "Video/audio is playing. Do you want to keep it playing after closing the app?" else "ভিডিও/অডিও প্লে হচ্ছে। অ্যাপ বন্ধ করার পরও এটা চালু রাখতে চান?") },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; activity?.finish() }) { Text(if (appLanguage == AppLanguageStore.ENGLISH) "Keep playing in background" else "ব্যাকগ্রাউন্ডে চালু রাখুন") }
            },
            dismissButton = {
                TextButton(onClick = {
                    controller?.stop()
                    nowPlaying = null
                    preparedUrl = null
                    NowPlayingStore.clear(context)
                    showExitConfirm = false
                    activity?.finish()
                }) { Text(if (appLanguage == AppLanguageStore.ENGLISH) "Stop and close" else "বন্ধ করুন") }
            }
        )
    }

    val video = nowPlaying

    CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
        Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = if (isFullscreen || shortsFullscreen) 0.dp else TOP_BAR_HEIGHT,
                    bottom = if (isFullscreen || shortsFullscreen) 0.dp else BOTTOM_BAR_HEIGHT
                )
        ) {
            when {
                playerExpanded && video != null -> {
                    PlayerScreen(
                        video = video,
                        controller = controller,
                        autoPlayEnabled = autoPlay,
                        isFullscreen = isFullscreen,
                        alreadyPrepared = preparedUrl == video.url,
                        onPrepared = { preparedUrl = it },
                    
                        hasPrevious = if (queue.isNotEmpty()) {
                            queueIndex > 0
                        } else {
                            playHistory.isNotEmpty()
                        },
                    
                        onPrevious = {
                            if (queue.isNotEmpty() && queueIndex > 0) {
                                playQueuePrevious()
                            } else {
                                playPrevious()
                            }
                        },
                    
                        queue = queue,
                        queueIndex = queueIndex,
                    
                        onQueueJump = { idx ->
                            playFromQueue(queue, idx)
                        },
                    
                        onFullscreenChange = { isFullscreen = it },
                    
                        onRelatedSelected = {
                            playVideo(it)
                        },
                    
                        onChannelSelected = { url ->
                            channelUrl = url
                            playerExpanded = false
                        }
                    )
                }
                !isFullscreen && channelUrl != null -> {
                    ChannelScreen(channelUrl = channelUrl!!, onVideoSelected = { playVideo(it) })
                }
                !isFullscreen -> {
                    when {
                        showSearch -> InlineSearchPanel(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            searchRequestToken = searchRequestToken,
                            onVideoSelected = { playVideo(it) },
                            onClose = { showSearch = false }
                        )
                        currentTab == BottomTab.HOME -> HomeScreen(onVideoSelected = { playVideo(it) })
                        currentTab == BottomTab.HISTORY -> HistoryScreen(onVideoSelected = { playVideo(it) })
                        currentTab == BottomTab.SUBSCRIPTIONS -> SubscriptionScreen(
                            onVideoSelected = { playVideo(it) },
                            onChannelSelected = { url -> channelUrl = url }
                        )
                        currentTab == BottomTab.SHORTS && shortsEnabled -> ShortsScreen(
                            controller = controller,
                            onFullscreenChange = { fullscreen ->
                                shortsFullscreen = fullscreen
                            }
                        )
                        currentTab == BottomTab.SAVED -> SavedScreen(onPlayPlaylist = { list, idx -> playFromQueue(list, idx) })
                        currentTab == BottomTab.PROFILE -> ProfileScreen(
                            darkMode = darkMode,
                            onDarkModeChange = onDarkModeChange,
                            autoplayNext = autoPlay,
                            onAutoplayNextChange = {
                                autoPlay = it
                                AppSettingsStore.setAutoplayNextDefault(context, it)
                            },
                            shortsEnabled = shortsEnabled,
                            onShortsEnabledChange = { enabled ->
                                shortsEnabled = enabled
                                AppSettingsStore.setShortsEnabled(context, enabled)
                                if (!enabled && currentTab == BottomTab.SHORTS) {
                                    currentTab = BottomTab.HOME
                                }
                            },
                            language = appLanguage,
                            onLanguageChange = { language ->
                                AppLanguageStore.set(context, language)
                                appLanguage = language
                            }
                        )
                    }
                }
            }
        }

        if (!isFullscreen && !shortsFullscreen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(TOP_BAR_HEIGHT)
                    .align(Alignment.TopCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showSearch || channelUrl != null) {
                    IconButton(onClick = { showSearch = false; channelUrl = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = if (appLanguage == AppLanguageStore.ENGLISH) "Back" else "পেছনে")
                    }
                }
                if (showSearch) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).height(48.dp),
                        placeholder = { Text(if (appLanguage == AppLanguageStore.ENGLISH) "Search videos..." else "ভিডিও সার্চ করুন...") },
                        singleLine = true,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = androidx.compose.ui.text.input.ImeAction.Search
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onSearch = { searchRequestToken++ }
                        )
                    )
                    IconButton(onClick = { searchRequestToken++ }) {
                        Icon(Icons.Default.Search, contentDescription = if (appLanguage == AppLanguageStore.ENGLISH) "Search" else "সার্চ")
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f).clickable { goHome() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(com.tubelite.app.R.drawable.ic_tubelite_logo),
                            contentDescription = "TubeLite",
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "TubeLite",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                    IconButton(onClick = { openSearch() }) {
                        Icon(Icons.Default.Search, contentDescription = if (appLanguage == AppLanguageStore.ENGLISH) "Search" else "সার্চ")
                    }
                    IconButton(onClick = {
                        currentTab = BottomTab.PROFILE
                        showSearch = false
                        playerExpanded = false
                        channelUrl = null
                    }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = if (appLanguage == AppLanguageStore.ENGLISH) "Profile" else "প্রোফাইল"
                        )
                    }
                }
            }
        }

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

        if (!isFullscreen && !shortsFullscreen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(BOTTOM_BAR_HEIGHT)
                    .align(Alignment.BottomCenter)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomBarItem(Icons.Default.Home, if (appLanguage == AppLanguageStore.ENGLISH) "Home" else "হোম", currentTab == BottomTab.HOME && !showSearch && channelUrl == null) {
                    currentTab = BottomTab.HOME; showSearch = false; playerExpanded = false; channelUrl = null
                }
                BottomBarItem(Icons.Default.History, if (appLanguage == AppLanguageStore.ENGLISH) "History" else "হিস্ট্রি", currentTab == BottomTab.HISTORY && !showSearch && channelUrl == null) {
                    currentTab = BottomTab.HISTORY; showSearch = false; playerExpanded = false; channelUrl = null
                }
                BottomBarItem(Icons.Default.Subscriptions, if (appLanguage == AppLanguageStore.ENGLISH) "Subscriptions" else "সাবস্ক্রিপশন", currentTab == BottomTab.SUBSCRIPTIONS && !showSearch && channelUrl == null) {
                    currentTab = BottomTab.SUBSCRIPTIONS; showSearch = false; playerExpanded = false; channelUrl = null
                }
                if (shortsEnabled) {
                    BottomBarItem(Icons.Default.PlayArrow, if (appLanguage == AppLanguageStore.ENGLISH) "Shorts" else "শর্টস", currentTab == BottomTab.SHORTS && !showSearch && channelUrl == null) {
                        currentTab = BottomTab.SHORTS; showSearch = false; playerExpanded = false; channelUrl = null
                    }
                }
                BottomBarItem(Icons.Default.PlaylistPlay, if (appLanguage == AppLanguageStore.ENGLISH) "Saved" else "সেভ", currentTab == BottomTab.SAVED && !showSearch && channelUrl == null) {
                    currentTab = BottomTab.SAVED; showSearch = false; playerExpanded = false; channelUrl = null
                }
                BottomBarItem(Icons.Default.AccountCircle, if (appLanguage == AppLanguageStore.ENGLISH) "Profile" else "প্রোফাইল", currentTab == BottomTab.PROFILE && !showSearch && channelUrl == null) {
                    currentTab = BottomTab.PROFILE; showSearch = false; playerExpanded = false; channelUrl = null
                }
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
