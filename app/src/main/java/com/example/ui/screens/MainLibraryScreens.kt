package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.database.FavoriteEntity
import com.example.database.HistoryEntity
import com.example.database.PlaylistEntity
import com.example.database.PlaylistVideoCrossRef
import com.example.models.FolderItem
import com.example.models.VideoItem
import com.example.viewmodels.MPlayerViewModel
import com.example.viewmodels.SortOrder
import java.io.File

@Composable
fun FoldersScreen(
    viewModel: MPlayerViewModel,
    onFolderClick: (FolderItem) -> Unit
) {
    val folders by viewModel.folders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("folders_screen_root")
    ) {
        if (folders.isEmpty() && !isScanning) {
            EmptyStateView(
                icon = Icons.Outlined.FolderOff,
                title = "No folders found",
                description = "Grant storage permission or copy videos to your device to begin."
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(folders) { folder ->
                    FolderCard(folder = folder, onClick = { onFolderClick(folder) })
                }
            }
        }

        if (isScanning) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun FolderCard(folder: FolderItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("folder_card_${folder.name}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = folder.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${folder.videoCount} videos",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun VideosScreen(
    viewModel: MPlayerViewModel,
    folder: FolderItem?,
    onVideoClick: (VideoItem) -> Unit
) {
    val videos by viewModel.filteredVideos.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    // Trigger local scan if not loaded yet
    LaunchedEffect(folder) {
        viewModel.selectFolder(folder)
        viewModel.triggerScan()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("videos_screen_root")
    ) {
        // Search and Sort Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search videos...") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, "Clear")
                        }
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(max = 56.dp)
                    .testTag("search_text_input"),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.testTag("sort_button")
                ) {
                    Icon(Icons.Default.Sort, "Sort")
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    SortMenuItem(SortOrder.NAME_ASC, "Name A-Z", sortOrder, viewModel) { showSortMenu = false }
                    SortMenuItem(SortOrder.NAME_DESC, "Name Z-A", sortOrder, viewModel) { showSortMenu = false }
                    SortMenuItem(SortOrder.DATE_DESC, "Newest First", sortOrder, viewModel) { showSortMenu = false }
                    SortMenuItem(SortOrder.DATE_ASC, "Oldest First", sortOrder, viewModel) { showSortMenu = false }
                    SortMenuItem(SortOrder.SIZE_DESC, "Size Max", sortOrder, viewModel) { showSortMenu = false }
                    SortMenuItem(SortOrder.SIZE_ASC, "Size Min", sortOrder, viewModel) { showSortMenu = false }
                    SortMenuItem(SortOrder.DURATION_DESC, "Longest First", sortOrder, viewModel) { showSortMenu = false }
                }
            }
        }

        if (videos.isEmpty() && !isScanning) {
            EmptyStateView(
                icon = Icons.Outlined.VideoLibrary,
                title = if (searchQuery.isNotEmpty()) "No search results" else "No videos found",
                description = "No digital files were found matching current filters."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(videos) { video ->
                    VideoListItem(
                        video = video,
                        onPlay = { onVideoClick(video) },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenuItem(
    order: SortOrder,
    label: String,
    current: SortOrder,
    viewModel: MPlayerViewModel,
    onDismiss: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontWeight = if (current == order) FontWeight.Bold else FontWeight.Normal)
                if (current == order) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp))
                }
            }
        },
        onClick = {
            viewModel.setSortOrder(order)
            onDismiss()
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoListItem(
    video: VideoItem,
    onPlay: () -> Unit,
    viewModel: MPlayerViewModel
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDetailsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onPlay,
                onLongClick = { showMenu = true }
            )
            .testTag("video_item_${video.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Video Frame Placeholder
            Box(
                modifier = Modifier
                    .size(90.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(28.dp)
                )

                // Duration Tag Badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = video.formattedDuration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = video.formattedSize,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.resolution ?: "HD",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Toggle Favorite Icon
            IconButton(onClick = { viewModel.toggleFavorite(video) }) {
                Icon(
                    imageVector = if (video.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (video.isFavorite) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Options menu trigger
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "More Options")
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Details Info") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = {
                            showMenu = false
                            showDetailsDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        leadingIcon = { Icon(Icons.Default.PlaylistAdd, null) },
                        onClick = {
                            showMenu = false
                            showAddToPlaylistDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename file") },
                        leadingIcon = { Icon(Icons.Default.Edit, null) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Private Vault") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        onClick = {
                            showMenu = false
                            viewModel.moveVideoToVault(video)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            showMenu = false
                            shareVideo(context, video)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                        colors = MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error
                        ),
                        onClick = {
                            showMenu = false
                            deleteVideoFile(context, video, viewModel)
                        }
                    )
                }
            }
        }
    }

    // Details Information Dialog
    if (showDetailsDialog) {
        VideoDetailsDialog(video = video, onDismiss = { showDetailsDialog = false })
    }

    // Rename Dialog
    if (showRenameDialog) {
        var newTitle by remember { mutableStateOf(video.title) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename File") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    showRenameDialog = false
                    // Rename logic wrapper
                    val file = File(video.path)
                    val parent = file.parentFile
                    val newFile = File(parent, newTitle)
                    if (file.renameTo(newFile)) {
                        viewModel.triggerScan()
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add To Playlist dialog
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            viewModel = viewModel,
            video = video,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }
}

@Composable
fun VideoDetailsDialog(video: VideoItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyRow("Name", video.title)
                PropertyRow("Location", video.path)
                PropertyRow("Format / Codec", video.mimeType ?: "unknown")
                PropertyRow("Resolution", video.resolution ?: "unknown")
                PropertyRow("Size", video.formattedSize)
                PropertyRow("Duration", video.formattedDuration)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        Text(value, fontSize = 13.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun PlaylistsScreen(
    viewModel: MPlayerViewModel,
    onPlaylistClick: (PlaylistEntity) -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("playlists_screen_root")
    ) {
        if (playlists.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.PlaylistPlay,
                title = "No custom playlists",
                description = "Organize, queue, and combine your favorite offline videos."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlists) { playlist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaylistClick(playlist) }
                            .testTag("playlist_card_${playlist.playlistId}"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.FeaturedPlayList, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Offline custom queue", fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { viewModel.deletePlaylist(playlist.playlistId) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("create_playlist_fab"),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.Add, "Create Playlist")
        }
    }

    if (showCreateDialog) {
        var playlistName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (playlistName.isNotEmpty()) {
                            viewModel.createPlaylist(playlistName)
                            showCreateDialog = false
                        }
                    },
                    enabled = playlistName.isNotEmpty()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PlaylistVideosScreen(
    viewModel: MPlayerViewModel,
    playlist: PlaylistEntity,
    onVideoClick: (VideoItem) -> Unit
) {
    val crossRefs by viewModel.getPlaylistVideos(playlist.playlistId).collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("playlist_videos_screen_root")
    ) {
        Text(
            text = playlist.name,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp)
        )

        if (crossRefs.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.PlaylistAdd,
                title = "Playlist is empty",
                description = "Long press videos in standard lists to append them to playlists."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(crossRefs) { ref ->
                    val videoItem = VideoItem(
                        id = ref.videoId,
                        title = ref.videoTitle,
                        path = ref.videoPath,
                        size = ref.size,
                        duration = ref.duration,
                        resolution = null,
                        mimeType = null,
                        folderName = ref.folderName,
                        dateAdded = System.currentTimeMillis()
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(videoItem) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ref.videoTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(videoItem.formattedDuration, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                viewModel.removeVideoFromPlaylist(playlist.playlistId, ref.videoPath)
                            }) {
                                Icon(Icons.Default.RemoveCircleOutline, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(
    viewModel: MPlayerViewModel,
    video: VideoItem,
    onDismiss: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("Create playlists from the Playlists tab first!")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(playlists) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addVideoToPlaylist(playlist.playlistId, video)
                                    onDismiss()
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FeaturedPlayList, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(playlist.name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun HistoryScreen(
    viewModel: MPlayerViewModel,
    onVideoClick: (VideoItem) -> Unit
) {
    val historyList by viewModel.history.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("history_screen_root")
    ) {
        if (historyList.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { viewModel.clearHistory() }) {
                    Icon(Icons.Default.ClearAll, null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear All")
                }
            }
        }

        if (historyList.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.History,
                title = "No watch history",
                description = "Videos you watch locally or via streaming links will show up here."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(historyList) { ref ->
                    val videoItem = VideoItem(
                        id = ref.videoId,
                        title = ref.title,
                        path = ref.videoPath,
                        size = ref.size,
                        duration = ref.duration,
                        resolution = null,
                        mimeType = null,
                        folderName = ref.folderName,
                        dateAdded = System.currentTimeMillis(),
                        lastPosition = ref.lastPlayedPosition
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(videoItem) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HistoryToggleOff, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ref.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("Resumes from: ${formatTime(ref.lastPlayedPosition)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                viewModel.removeFromHistory(ref.videoPath)
                            }) {
                                Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    viewModel: MPlayerViewModel,
    onVideoClick: (VideoItem) -> Unit
) {
    val favoritesList by viewModel.favorites.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("favorites_screen_root")
    ) {
        if (favoritesList.isEmpty()) {
            EmptyStateView(
                icon = Icons.Outlined.Favorite,
                title = "No favorites",
                description = "Tap the heart icon on any video to keep tabs on your absolute favorites."
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favoritesList) { ref ->
                    val videoItem = VideoItem(
                        id = ref.videoId,
                        title = ref.title,
                        path = ref.videoPath,
                        size = ref.size,
                        duration = ref.duration,
                        resolution = null,
                        mimeType = null,
                        folderName = ref.folderName,
                        dateAdded = ref.dateAdded,
                        isFavorite = true
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVideoClick(videoItem) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Favorite, null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(ref.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(videoItem.formattedDuration, fontSize = 12.sp, color = Color.Gray)
                            }
                            IconButton(onClick = {
                                viewModel.removeFavoriteByPath(ref.videoPath)
                            }) {
                                Icon(Icons.Default.FavoriteBorder, "Unfavorite", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkScreen(onStreamClick: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    val streamedLinks = remember { mutableStateListOf<String>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("network_screen_root"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Icon(Icons.Default.RssFeed, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Network Streaming", fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            text = "Enter any HTTP, HTTPS, HLS, DASH, or RTSP streaming links to initiate playback inside ExoPlayer instantly.",
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Streaming URL") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stream_url_input"),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            trailingIcon = {
                if (url.isNotEmpty()) {
                    IconButton(onClick = { url = "" }) {
                        Icon(Icons.Default.Close, null)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (url.isNotEmpty()) {
                    streamedLinks.add(url)
                    onStreamClick(url)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("stream_play_button"),
            enabled = url.isNotEmpty(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Initiate Player")
        }

        if (streamedLinks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text("Recent Streams", fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(streamedLinks) { link ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStreamClick(link) }
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(link, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Decoder configuration
    var hardwareDecoding by remember { mutableStateOf(true) }
    var audioBoost by remember { mutableStateOf(false) }

    // Playback preferences
    var backgroundPlayback by remember { mutableStateOf(false) }
    var gestureSeeking by remember { mutableStateOf(true) }
    var autoPlayNext by remember { mutableStateOf(true) }
    var doubleTapSeekSeconds by remember { mutableStateOf(10) }

    // Subtitle styling preferences
    var subtitleTextScale by remember { mutableStateOf("Default") }
    var subtitleColor by remember { mutableStateOf("White") }

    // Display preferences
    var autoRotate by remember { mutableStateOf(true) }
    var keepScreenOn by remember { mutableStateOf(true) }

    // Appearance Accent Theme selection
    var selectedAccent by remember { mutableStateOf("Purple") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("settings_screen_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Preferences",
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        // CARD 1: DECODER CONFIGURATION
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Hardware,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Decoder Configuration",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Acceleration", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Leverage device GPU chipsets for optimal hardware decoding.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = hardwareDecoding, onCheckedChange = { hardwareDecoding = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio Boost (SW)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Amplify software maximum volume level up to 200%.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = audioBoost, onCheckedChange = { audioBoost = it })
                }
            }
        }

        // CARD 2: PLAYER & PLAYBACK
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Player & Playback",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background Audio", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Continue playing video audio stream when switching apps.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = backgroundPlayback, onCheckedChange = { backgroundPlayback = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Swipe Gestures", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Control brightness, volume, and seek by swiping on screen.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = gestureSeeking, onCheckedChange = { gestureSeeking = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-play Next Video", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Automatically play the next file in playlist queue.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = autoPlayNext, onCheckedChange = { autoPlayNext = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Double tap seek selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Double-tap Seek Jump", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Select time interval to seek forward/backward.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(5, 10, 15, 30).forEach { seconds ->
                            val isSelected = doubleTapSeekSeconds == seconds
                            OutlinedButton(
                                onClick = { doubleTapSeekSeconds = seconds },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("${seconds}s", fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }

        // CARD 3: SUBTITLE STYLING
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Subtitle Styling",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Text Scale Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Caption Text Size", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Compact", "Default", "Large").forEach { scale ->
                            val isSelected = subtitleTextScale == scale
                            OutlinedButton(
                                onClick = { subtitleTextScale = scale },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(scale, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color Selector
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Caption Text Color", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val colorsList = listOf(
                            "White" to Color.White,
                            "Yellow" to Color.Yellow,
                            "Green" to Color.Green,
                            "Cyan" to Color.Cyan
                        )
                        colorsList.forEach { (colorName, colorVal) ->
                            val isSelected = subtitleColor == colorName
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .clickable { subtitleColor = colorName }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        // CARD 4: DISPLAY PREFERENCES
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Display & Screen",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-rotate Screen", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Automatically switch player orientation to fit video aspect ratio.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = autoRotate, onCheckedChange = { autoRotate = it })
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep Screen Awake", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Prevent device from turning off display while video is playing.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Switch(checked = keepScreenOn, onCheckedChange = { keepScreenOn = it })
                }
            }
        }

        // CARD 5: THEME ACCENTS
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App Interface Customization",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Elegant Dark Accent Color", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Select a color palette theme for buttons and cards.", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        val accentColors = listOf(
                            "Purple" to Color(0xFFD0BCFF),
                            "Sunset" to Color(0xFFFFB866),
                            "Emerald" to Color(0xFF81C784),
                            "NeonBlue" to Color(0xFF4FC3F7),
                            "Rose" to Color(0xFFFF8A80)
                        )
                        accentColors.forEach { (name, colorVal) ->
                            val isSelected = selectedAccent == name
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorVal)
                                    .clickable { selectedAccent = name }
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // UTILITIES BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    hardwareDecoding = true
                    audioBoost = false
                    backgroundPlayback = false
                    gestureSeeking = true
                    autoPlayNext = true
                    doubleTapSeekSeconds = 10
                    subtitleTextScale = "Default"
                    subtitleColor = "White"
                    autoRotate = true
                    keepScreenOn = true
                    selectedAccent = "Purple"
                    Toast.makeText(context, "Preferences have been reset to factory defaults", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset All", fontSize = 12.sp)
            }

            Button(
                onClick = {
                    Toast.makeText(context, "M4 Player is up to date! (v1.0.0-Stable)", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Check Update", fontSize = 12.sp)
            }
        }

        // ABOUT CARD
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "App & About",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("M4 Player Premium Video Player v1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Engine: Jetpack Media3 (ExoPlayer)", fontSize = 12.sp, color = Color.Gray)
                Text("Build target: Android SDK API 36", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Crafted with Jetpack Compose & Material Design 3. Custom high-performance codecs and gestures.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun VaultScreen(
    viewModel: MPlayerViewModel,
    onVideoClick: (VideoItem) -> Unit
) {
    val isVaultConfigured by viewModel.isVaultConfigured.collectAsState()
    val isVaultUnlocked by viewModel.isVaultUnlocked.collectAsState()
    val vaultVideos by viewModel.vaultVideos.collectAsState()

    var pinText by remember { mutableStateOf("") }
    var confirmPinText by remember { mutableStateOf("") }
    var setupMode by remember { mutableStateOf(!isVaultConfigured) }
    var unlockError by remember { mutableStateOf(false) }

    LaunchedEffect(isVaultConfigured) {
        setupMode = !isVaultConfigured
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("vault_screen_root")
    ) {
        if (!isVaultUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Lock, "Secure Vault", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (setupMode) "Configure PIN Code" else "Vault Protected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = if (setupMode) "Establish a 4-digit numeric PIN lock to safe keep files." else "Verify PIN to view files.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = pinText,
                    onValueChange = { if (it.length <= 4) pinText = it },
                    label = { Text("4-digit PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = unlockError,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .testTag("vault_pin_input")
                )

                if (setupMode) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPinText,
                        onValueChange = { if (it.length <= 4) confirmPinText = it },
                        label = { Text("Confirm 4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.8f)
                    )
                }

                if (unlockError) {
                    Text("Incorrect PIN. Please retry.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (setupMode) {
                            if (pinText.length == 4 && pinText == confirmPinText) {
                                viewModel.configureVault(pinText)
                            } else {
                                unlockError = true
                            }
                        } else {
                            viewModel.unlockVault(pinText) { success ->
                                if (success) {
                                    unlockError = false
                                } else {
                                    unlockError = true
                                }
                            }
                        }
                    },
                    enabled = pinText.length == 4,
                    modifier = Modifier.fillMaxWidth(0.6f)
                ) {
                    Text(if (setupMode) "Create" else "Unlock")
                }
            }
        } else {
            // Vault Unlocked: Show Vault Videos list
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Secure Private Vault", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = { viewModel.lockVault() }) {
                        Icon(Icons.Filled.Lock, "Lock Vault")
                    }
                }

                if (vaultVideos.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Outlined.Lock,
                        title = "Vault is empty",
                        description = "Move videos into this private folder by long-pressing items in the videos dashboard and choosing 'Move to Private Vault'."
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(vaultVideos) { video ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onVideoClick(video) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Security, "Secure file", tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(video.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(video.formattedSize, fontSize = 12.sp, color = Color.Gray)
                                    }
                                    IconButton(onClick = {
                                        viewModel.restoreVideoFromVault(video.path)
                                    }) {
                                        Icon(Icons.Default.LockOpen, "Restore to Public Gallery", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun shareVideo(context: Context, video: VideoItem) {
    try {
        val file = File(video.path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType ?: "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Video"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun deleteVideoFile(context: Context, video: VideoItem, viewModel: MPlayerViewModel) {
    try {
        val file = File(video.path)
        if (file.exists() && file.delete()) {
            viewModel.triggerScan()
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
