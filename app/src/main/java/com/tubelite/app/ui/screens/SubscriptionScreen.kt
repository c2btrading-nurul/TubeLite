package com.tubelite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubelite.app.data.Subscription
import com.tubelite.app.data.SubscriptionStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository

@Composable
fun SubscriptionScreen(
    onVideoSelected: (VideoResult) -> Unit,
    onChannelSelected: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var subscriptions by remember { mutableStateOf(SubscriptionStore.getAll(context)) }
    var videos by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() {
        subscriptions = SubscriptionStore.getAll(context)
    }

    LaunchedEffect(subscriptions) {
        loading = true
        val loaded = mutableListOf<VideoResult>()
        subscriptions.forEach { sub ->
            try {
                loaded += YoutubeRepository.getChannelVideos(sub.channelUrl, 12)
            } catch (_: Exception) { }
        }
        videos = loaded.distinctBy { it.url }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        if (subscriptions.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "আপনার সাবস্ক্রিপশন",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                items(subscriptions) { sub ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onChannelSelected(sub.channelUrl) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = sub.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(sub.channelName, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                item {
                    Text(
                        "সাম্প্রতিক ভিডিও",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                items(videos) { video ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onVideoSelected(video) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        VideoThumbnail(
                            video = video,
                            modifier = Modifier.width(140.dp).height(80.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                video.title,
                                maxLines = 2,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                video.uploaderName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("এখনও কোনো চ্যানেল সাবস্ক্রাইব করা হয়নি")
            }
        }
    }
}
