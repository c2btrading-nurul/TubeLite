package com.tubelite.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubelite.app.data.SubscriptionStore
import com.tubelite.app.data.VideoResult
import com.tubelite.app.data.YoutubeRepository

@Composable
fun SubscriptionScreen(
    onVideoSelected: (VideoResult) -> Unit,
    onChannelSelected: (String) -> Unit
) {
    val context = LocalContext.current
    var subscriptions by remember { mutableStateOf(SubscriptionStore.getAll(context)) }
    var videos by remember { mutableStateOf<List<VideoResult>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    suspend fun load() {
        loading = true
        subscriptions = SubscriptionStore.getAll(context)
        val loaded = mutableListOf<VideoResult>()
        subscriptions.forEach { sub ->
            try {
                loaded += YoutubeRepository.getChannelVideos(sub.channelUrl, 16)
            } catch (_: Exception) { }
        }
        videos = loaded.distinctBy { it.url }
        loading = false
    }

    LaunchedEffect(refreshKey) { load() }

    Column(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("আপনার সাবস্ক্রিপশন", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (subscriptions.isEmpty()) "চ্যানেল সাবস্ক্রাইব করলে নতুন ভিডিও এখানে পাবেন" else "সাবস্ক্রাইব করা চ্যানেল ও নতুন ভিডিও",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (loading && subscriptions.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (subscriptions.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("এখনও কোনো চ্যানেল সাবস্ক্রাইব করা হয়নি", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "কোনো চ্যানেলের পেজে গিয়ে Subscribe চাপুন।",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("চ্যানেল", style = MaterialTheme.typography.titleSmall)
            }
            items(subscriptions, key = { it.channelUrl }) { sub ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
                        .clickable { onChannelSelected(sub.channelUrl) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = sub.avatarUrl,
                        contentDescription = null,
                        modifier = Modifier.size(46.dp).clip(CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sub.channelName, style = MaterialTheme.typography.bodyLarge)
                        Text("সাবস্ক্রাইব করা", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text("সাম্প্রতিক ভিডিও", style = MaterialTheme.typography.titleSmall)
            }

            items(videos, key = { it.url }) { video ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onVideoSelected(video) }
                        .padding(6.dp)
                ) {
                    VideoThumbnail(
                        video = video,
                        modifier = Modifier.width(148.dp).height(84.dp).clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                        Text(video.title, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            video.uploaderName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.68f)
                        )
                    }
                }
            }
        }
    }
}
