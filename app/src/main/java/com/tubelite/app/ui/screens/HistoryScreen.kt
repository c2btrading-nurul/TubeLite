package com.tubelite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.WatchHistoryStore

@Composable
fun HistoryScreen(onVideoSelected: (VideoResult) -> Unit) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WatchHistoryStore.getAll(context)) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("দেখার ইতিহাস", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = {
                WatchHistoryStore.clear(context)
                history = emptyList()
            }) { Text("সব মুছুন") }
        }

        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("এখনো কিছু দেখা হয়নি")
            }
        } else {
            LazyColumn(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history) { v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onVideoSelected(v) }.padding(vertical = 4.dp)
                    ) {
                        VideoThumbnail(
                            video = v,
                            modifier = Modifier
                                .width(140.dp)
                                .height(80.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                v.uploaderName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}
