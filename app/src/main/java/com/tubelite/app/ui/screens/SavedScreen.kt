package com.tubelite.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tubelite.app.data.PlaylistStore
import com.tubelite.app.data.VideoResult

@Composable
fun SavedScreen(onPlayPlaylist: (List<VideoResult>, Int) -> Unit) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(PlaylistStore.getPlaylistNames(context)) }
    var openPlaylist by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun refresh() { playlists = PlaylistStore.getPlaylistNames(context) }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("নতুন প্লে-লিস্ট") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, placeholder = { Text("নাম দিন") }) },
            confirmButton = {
                TextButton(onClick = {
                    PlaylistStore.createPlaylist(context, newName.trim())
                    newName = ""
                    showCreateDialog = false
                    refresh()
                }) { Text("তৈরি করুন") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("বাতিল") } }
        )
    }

    val current = openPlaylist
    if (current != null) {
        var videos by remember(current) { mutableStateOf(PlaylistStore.getVideos(context, current)) }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { openPlaylist = null }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(current, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    PlaylistStore.deletePlaylist(context, current)
                    openPlaylist = null
                    refresh()
                }) { Icon(Icons.Default.Delete, contentDescription = "Delete playlist") }
            }
            if (videos.isNotEmpty()) {
                Button(onClick = { onPlayPlaylist(videos, 0) }, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("সবগুলো চালান")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (videos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("এই প্লে-লিস্টে কোনো ভিডিও নেই")
                }
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(videos) { index, v ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPlayPlaylist(videos, index) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VideoThumbnail(
                                video = v,
                                modifier = Modifier.width(120.dp).height(70.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(v.title, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                                Text(v.uploaderName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                            }
                            IconButton(onClick = {
                                PlaylistStore.removeVideo(context, current, v.url)
                                videos = PlaylistStore.getVideos(context, current)
                            }) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                        }
                    }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("সেভ করা প্লে-লিস্ট", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "New playlist")
            }
        }
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো প্লে-লিস্ট নেই। + চেপে একটা তৈরি করুন।")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists) { name ->
                    val count = PlaylistStore.getVideos(context, name).size
                    Row(
                        Modifier.fillMaxWidth().clickable { openPlaylist = name }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PlaylistPlay, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                            Text("$count ভিডিও", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
