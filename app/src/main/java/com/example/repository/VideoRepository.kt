package com.example.repository

import android.content.Context
import android.provider.MediaStore
import com.example.database.*
import com.example.models.FolderItem
import com.example.models.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

class VideoRepository(
    private val context: Context,
    private val dao: MPlayerDao
) {
    // Favorites Flows
    val favoritesFlow: Flow<List<FavoriteEntity>> = dao.getFavorites()
    
    // Playback History Flow
    val historyFlow: Flow<List<HistoryEntity>> = dao.getHistory()

    // Playlists Flows
    val playlistsFlow: Flow<List<PlaylistEntity>> = dao.getPlaylists()

    // Private Vault Flow
    val vaultVideosFlow: Flow<List<VaultVideoEntity>> = dao.getVaultVideosFlow()

    // Network Streams Flows
    val networkStreamsFlow: Flow<List<NetworkStreamEntity>> = dao.getNetworkStreams()

    suspend fun addNetworkStream(title: String, url: String) {
        dao.insertNetworkStream(NetworkStreamEntity(title = title, url = url))
    }

    suspend fun deleteNetworkStream(id: Long) {
        dao.deleteNetworkStream(id)
    }

    // Query local videos from device MediaStore, ignoring those moved into vault
    suspend fun scanLocalVideos(filterPrivate: Boolean = true): List<VideoItem> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoItem>()
        val vaultVideos = if (filterPrivate) {
            dao.getVaultVideos().map { it.videoPath }.toSet()
        } else {
            emptySet()
        }

        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.RESOLUTION,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val resCol = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val folderCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val dateCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val path = if (dataCol != -1) cursor.getString(dataCol) else ""
                    if (path.isEmpty()) continue

                    // If we're filtering, and the video is in vault, skip it
                    if (filterPrivate && vaultVideos.contains(path)) {
                        continue
                    }

                    val id = if (idCol != -1) cursor.getLong(idCol) else 0L
                    val name = if (nameCol != -1) cursor.getString(nameCol) ?: "Video_$id" else "Video_$id"
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L
                    val duration = if (durationCol != -1) cursor.getLong(durationCol) else 0L
                    val resolution = if (resCol != -1) cursor.getString(resCol) else null
                    val mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else null
                    val folderName = if (folderCol != -1) cursor.getString(folderCol) ?: "Internal Storage" else "Internal Storage"
                    val dateAdded = if (dateCol != -1) cursor.getLong(dateCol) * 1000 else System.currentTimeMillis()

                    val isFav = dao.isFavorite(path)
                    val lastPos = dao.getLastPosition(path) ?: 0L

                    videos.add(
                        VideoItem(
                            id = id,
                            title = name,
                            path = path,
                            size = size,
                            duration = duration,
                            resolution = resolution,
                            mimeType = mimeType,
                            folderName = folderName,
                            dateAdded = dateAdded,
                            isFavorite = isFav,
                            lastPosition = lastPos,
                            isPrivate = !filterPrivate && vaultVideos.contains(path)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        videos
    }

    suspend fun getFolders(videos: List<VideoItem>): List<FolderItem> = withContext(Dispatchers.IO) {
        videos.groupBy { it.folderName }.map { (folderName, items) ->
            val firstItem = items.firstOrNull()
            val parentPath = firstItem?.path?.let { File(it).parent } ?: ""
            FolderItem(
                name = folderName,
                path = parentPath,
                videoCount = items.size
            )
        }.sortedBy { it.name }
    }

    // Favorites Operations
    suspend fun addToFavorites(video: VideoItem) {
        dao.insertFavorite(
            FavoriteEntity(
                videoPath = video.path,
                title = video.title,
                videoId = video.id,
                duration = video.duration,
                size = video.size,
                folderName = video.folderName,
                dateAdded = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFromFavorites(videoPath: String) {
        dao.deleteFavorite(videoPath)
    }

    suspend fun isFavorite(videoPath: String): Boolean {
        return dao.isFavorite(videoPath)
    }

    // History Operations
    suspend fun addToHistory(video: VideoItem, position: Long) {
        dao.insertHistory(
            HistoryEntity(
                videoPath = video.path,
                title = video.title,
                videoId = video.id,
                duration = video.duration,
                size = video.size,
                folderName = video.folderName,
                lastPlayedPosition = position,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeFromHistory(videoPath: String) {
        dao.deleteHistory(videoPath)
    }

    suspend fun clearHistory() {
        dao.clearHistory()
    }

    suspend fun getLastPlayedPosition(videoPath: String): Long {
        return dao.getLastPosition(videoPath) ?: 0L
    }

    // Playlist Operations
    suspend fun createPlaylist(name: String): Long {
        return dao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
        dao.clearPlaylistVideos(playlistId)
    }

    fun getPlaylistVideosFlow(playlistId: Long): Flow<List<PlaylistVideoCrossRef>> {
        return dao.getPlaylistVideos(playlistId)
    }

    suspend fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        dao.insertPlaylistVideo(
            PlaylistVideoCrossRef(
                playlistId = playlistId,
                videoPath = video.path,
                videoTitle = video.title,
                videoId = video.id,
                duration = video.duration,
                size = video.size,
                folderName = video.folderName
            )
        )
    }

    suspend fun removeVideoFromPlaylist(playlistId: Long, videoPath: String) {
        dao.removeVideoFromPlaylist(playlistId, videoPath)
    }

    // Vault PIN Operations
    suspend fun isVaultConfigured(): Boolean {
        return dao.getVaultSetting() != null
    }

    suspend fun configureVaultPin(pin: String) {
        dao.saveVaultSetting(VaultSettingEntity(pinCode = pin))
    }

    suspend fun verifyVaultPin(pin: String): Boolean {
        val setting = dao.getVaultSetting()
        return setting?.pinCode == pin
    }

    // Vault Video Operations
    suspend fun addVideoToVault(videoPath: String) {
        dao.addVideoToVault(VaultVideoEntity(videoPath = videoPath))
    }

    suspend fun removeVideoFromVault(videoPath: String) {
        dao.removeVideoFromVault(videoPath)
    }

    suspend fun isVideoInVault(videoPath: String): Boolean {
        return dao.isVideoInVault(videoPath)
    }

    suspend fun scanVaultVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        // Scanners all device videos (including those hidden in vault)
        val allVideos = scanLocalVideos(filterPrivate = false)
        val vaultPaths = dao.getVaultVideos().map { it.videoPath }.toSet()
        allVideos.filter { vaultPaths.contains(it.path) }
    }
}
