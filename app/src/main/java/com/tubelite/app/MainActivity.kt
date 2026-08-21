package com.tubelite.app

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.tubelite.app.data.AppLanguageStore
import com.tubelite.app.data.AppSettingsStore
import com.tubelite.app.data.NowPlayingStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.service.PlaybackService
import com.tubelite.app.ui.screens.ChannelScreen
import com.tubelite.app.ui.screens.HistoryScreen
import com.tubelite.app.ui.screens.HomeScreen
import com.tubelite.app.ui.screens.InlineSearchPanel
import com.tubelite.app.ui.screens.LocalAppLanguage
import com.tubelite.app.ui.screens.MiniPlayerBar
import com.tubelite.app.ui.screens.PlayerScreen
import com.tubelite.app.ui.screens.ProfileScreen
import com.tubelite.app.ui.screens.SavedScreen
import com.tubelite.app.ui.screens.SubscriptionScreen
import com.tubelite.app.ui.theme.TubeLiteTheme

private enum class BottomTab { HOME, HISTORY, SUBSCRIPTIONS, SAVED, PROFILE }

private val TOP_BAR_HEIGHT = 64.dp
private val BOTTOM_BAR_HEIGHT = 68.dp

class MainActivity : ComponentActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var darkMode by remember { mutableStateOf(AppSettingsStore.isDarkMode(this)) }
            TubeLiteTheme(darkTheme = darkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
private fun AppRoot(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    var nowPlaying by remember { mutableStateOf<VideoResult?>(null) }
    var playerExpanded by remember { mutableStateOf(false) }
    var autoPlay by remember { mutableStateOf(AppSettingsStore.isAutoplayNextDefault(context)) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchRequestToken by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
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

    fun playVideo(
        v: VideoResult,
        addToHistory: Boolean = true,
        clearQueue: Boolean = true
    ) {
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
        if (index !in list.indices) return
        queue = list
        queueIndex = index
        playVideo(list[index], clearQueue = false)
    }

    fun playPrevious() {
        val prev = playHistory.lastOrNull() ?: return
        playHistory = playHistory.dropLast(1)
        playVideo(prev, addToHistory = false)
    }

    BackHandler(enabled = true) {
        when {
            isFullscreen -> isFullscreen = false
            currentTab != BottomTab.HOME || showSearch || channelUrl != null || playerExpanded -> goHome()
            controller?.isPlaying == true -> showExitConfirm = true
            else -> activity?.finish()
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = {
                Text(
                    if (appLanguage == AppLanguageStore.ENGLISH)
                        "Keep playing in background?"
                    else
                        "ব্যাকগ্রাউন্ডে চালু রাখবেন?"
                )
            },
            text = {
                Text(
                    if (appLanguage == AppLanguageStore.ENGLISH)
                        "Video/audio is playing. Do you want to keep it playing after closing the app?"
                    else
                        "ভিডিও/অডিও প্লে হচ্ছে। অ্যাপ বন্ধ করার পরও এটা চালু রাখতে চান?"
                )
            },
            confirmButton = {
                TextButton(onClick = { showExitConfirm = false; activity?.finish() }) {
                    Text(
                        if (appLanguage == AppLanguageStore.ENGLISH)
                            "Keep playing in background"
                        else
                            "ব্যাকগ্রাউন্ডে চালু রাখুন"
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    controller?.stop()
                    nowPlaying = null
                    preparedUrl = null
                    NowPlayingStore.clear(context)
                    showExitConfirm = false
                    activity?.finish()
                }) {
                    Text(if (appLanguage == AppLanguageStore.ENGLISH) "Stop and close" else "বন্ধ করুন")
                }
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
                        top = if (isFullscreen) 0.dp else TOP_BAR_HEIGHT,
                        bottom = if (isFullscreen) 0.dp else BOTTOM_BAR_HEIGHT
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
                            hasPrevious = playHistory.isNotEmpty(),
                            onPrevious = { playPrevious() },
                            queue = queue,
                            queueIndex = queueIndex,
                            onQueueJump = { idx -> playFromQueue(queue, idx) },
                            onFullscreenChange = { isFullscreen = it },
                            onRelatedSelected = { playVideo(it) },
                            onChannelSelected = { url -> channelUrl = url; playerExpanded = false }
                        )
                    }
                    !isFullscreen && channelUrl != null -> {
                        ChannelScreen(
                            channelUrl = channelUrl!!,
                            onVideoSelected = { playVideo(it) }
                        )
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
                            currentTab == BottomTab.SAVED -> SavedScreen(
                                onPlayPlaylist = { list, idx -> playFromQueue(list, idx) }
                            )
                            currentTab == BottomTab.PROFILE -> ProfileScreen(
                                darkMode = darkMode,
                                onDarkModeChange = onDarkModeChange,
                                autoplayNext = autoPlay,
                                onAutoplayNextChange = {
                                    autoPlay = it
                                    AppSettingsStore.setAutoplayNextDefault(context, it)
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

            if (!isFullscreen) {
                TubeLiteTopBar(
                    modifier = Modifier.align(Alignment.TopCenter),
                    language = appLanguage,
                    showSearch = showSearch,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = { searchRequestToken++ },
                    onBack = {
                        showSearch = false
                        channelUrl = null
                    },
                    onOpenSearch = { openSearch() },
                    onGoHome = { goHome() }
                )
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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = BOTTOM_BAR_HEIGHT)
                )
            }

            if (!isFullscreen) {
                TubeLiteBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    language = appLanguage,
                    currentTab = currentTab,
                    enabled = !playerExpanded,
                    onTabSelected = { tab ->
                        currentTab = tab
                        showSearch = false
                        playerExpanded = false
                        channelUrl = null
                    }
                )
            }
        }
    }
}

@Composable
private fun TubeLiteTopBar(
    modifier: Modifier = Modifier,
    language: String,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenSearch: () -> Unit,
    onGoHome: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(TOP_BAR_HEIGHT),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        if (showSearch) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = if (language == AppLanguageStore.ENGLISH) "Back" else "পেছনে"
                    )
                }
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f).height(48.dp),
                    placeholder = {
                        Text(
                            if (language == AppLanguageStore.ENGLISH)
                                "Search videos..."
                            else
                                "ভিডিও সার্চ করুন..."
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { onSearch() }
                    )
                )
                IconButton(onClick = onSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = if (language == AppLanguageStore.ENGLISH) "Search" else "সার্চ"
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onGoHome)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(com.tubelite.app.R.drawable.ic_tubelite_logo),
                        contentDescription = "TubeLite",
                        modifier = Modifier.size(38.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "TubeLite",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        letterSpacing = (-0.5).sp
                    )
                }

                IconButton(onClick = { /* visual header action */ }) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = if (language == AppLanguageStore.ENGLISH) "Cast" else "কাস্ট"
                    )
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = if (language == AppLanguageStore.ENGLISH) "Search" else "সার্চ"
                    )
                }
                IconButton(onClick = { /* reserved for future menu */ }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = if (language == AppLanguageStore.ENGLISH) "More" else "আরও"
                    )
                }
            }
        }
    }
}

@Composable
private fun TubeLiteBottomBar(
    modifier: Modifier = Modifier,
    language: String,
    currentTab: BottomTab,
    enabled: Boolean,
    onTabSelected: (BottomTab) -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(BOTTOM_BAR_HEIGHT),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 5.dp,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarItem(
                Icons.Default.Home,
                if (language == AppLanguageStore.ENGLISH) "Home" else "হোম",
                currentTab == BottomTab.HOME,
                enabled
            ) { onTabSelected(BottomTab.HOME) }
            BottomBarItem(
                Icons.Default.History,
                if (language == AppLanguageStore.ENGLISH) "History" else "হিস্ট্রি",
                currentTab == BottomTab.HISTORY,
                enabled
            ) { onTabSelected(BottomTab.HISTORY) }
            BottomBarItem(
                Icons.Default.Subscriptions,
                if (language == AppLanguageStore.ENGLISH) "Subscriptions" else "সাবস্ক্রিপশন",
                currentTab == BottomTab.SUBSCRIPTIONS,
                enabled
            ) { onTabSelected(BottomTab.SUBSCRIPTIONS) }
            BottomBarItem(
                Icons.Default.PlaylistPlay,
                if (language == AppLanguageStore.ENGLISH) "Save" else "সেভ",
                currentTab == BottomTab.SAVED,
                enabled
            ) { onTabSelected(BottomTab.SAVED) }
            BottomBarItem(
                Icons.Default.AccountCircle,
                if (language == AppLanguageStore.ENGLISH) "Profile" else "প্রোফাইল",
                currentTab == BottomTab.PROFILE,
                enabled
            ) { onTabSelected(BottomTab.PROFILE) }
        }
    }
}

@Composable
private fun BottomBarItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
    val color = if (selected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .widthIn(min = 58.dp, max = 86.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .height(30.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (selected) Modifier.background(activeColor.copy(alpha = 0.14f)) else Modifier
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}
