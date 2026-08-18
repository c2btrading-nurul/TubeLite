package com.tubelite.app

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tubelite.app.data.VideoResult
import com.tubelite.app.ui.screens.HomeScreen
import com.tubelite.app.ui.screens.PlayerScreen
import com.tubelite.app.ui.screens.SearchScreen
import com.tubelite.app.ui.theme.TubeLiteTheme

class MainActivity : ComponentActivity() {

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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().build()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    var selectedVideo by remember { mutableStateOf<VideoResult?>(null) }
    var autoPlay by remember { mutableStateOf(true) }
    var showSearch by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                if (showSearch) {
                    Text("সার্চ")
                } else {
                    Text("TubeLite", fontWeight = FontWeight.Bold)
                }
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

        val video = selectedVideo
        if (video != null) {
            PlayerScreen(video = video, autoPlayEnabled = autoPlay, onEnterPip = {})
            TextButton(onClick = { selectedVideo = null }) { Text("← ফিরে যান") }
        }

        Box(Modifier.fillMaxSize()) {
            if (showSearch) {
                SearchScreen(onVideoSelected = {
                    selectedVideo = it
                    showSearch = false
                })
            } else {
                HomeScreen(onVideoSelected = { selectedVideo = it })
            }
        }
    }
}
