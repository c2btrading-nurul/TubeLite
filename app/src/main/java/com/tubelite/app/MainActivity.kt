package com.tubelite.app

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tubelite.app.data.VideoResult
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot() {
    var selectedVideo by remember { mutableStateOf<VideoResult?>(null) }
    var autoPlay by remember { mutableStateOf(true) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("TubeLite") },
            actions = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Autoplay", modifier = Modifier.padding(end = 4.dp))
                    Switch(checked = autoPlay, onCheckedChange = { autoPlay = it })
                }
            }
        )

        val video = selectedVideo
        if (video != null) {
            PlayerScreen(video = video, autoPlayEnabled = autoPlay, onEnterPip = {})
            TextButton(onClick = { selectedVideo = null }) { Text("← সার্চে ফিরুন") }
        }

        SearchScreen(onVideoSelected = { selectedVideo = it })
    }
}
