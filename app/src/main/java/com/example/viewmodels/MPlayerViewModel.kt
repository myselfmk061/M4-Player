package com.example.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.FavoriteEntity
import com.example.database.HistoryEntity
import com.example.database.PlaylistEntity
import com.example.database.PlaylistVideoCrossRef
import com.example.models.FolderItem
import com.example.models.VideoItem
import com.example.repository.VideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOrder {
    NAME_ASC, NAME_DESC,
    DATE_ASC, DATE_DESC,
    SIZE_ASC, SIZE_DESC,
    DURATION_ASC, DURATION_DESC
}

class MPlayerViewModel(
    application: Application,
    private val repository: VideoRepository
) : AndroidViewModel(application) {

    // UI States
    private val _allVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val allVideos: StateFlow<List<VideoItem>> = _allVideos.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlistsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<HistoryEntity>> = repository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favorites: StateFlow<List<FavoriteEntity>> = repository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Filtering
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedFolder = MutableStateFlow<FolderItem?>(null)
    val selectedFolder: StateFlow<FolderItem?> = _selectedFolder.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Private Vault States
    private val _isVaultUnlocked = MutableStateFlow(false)
    val isVaultUnlocked: StateFlow<Boolean> = _isVaultUnlocked.asStateFlow()

    private val _vaultVideos = MutableStateFlow<List<VideoItem>>(emptyList())
    val vaultVideos: StateFlow<List<VideoItem>> = _vaultVideos.asStateFlow()

    private val _isVaultConfigured = MutableStateFlow(false)
    val isVaultConfigured: StateFlow<Boolean> = _isVaultConfigured.asStateFlow()

    // Filtered & Sorted Videos Flow
    val filteredVideos: StateFlow<List<VideoItem>> = combine(
        _allVideos,
        _searchQuery,
        _sortOrder,
        _selectedFolder
    ) { videos, query, sort, folder ->
        var result = videos

        // Filter by Folder
        if (folder != null) {
            result = result.filter { it.folderName == folder.name }
        }

        // Filter by Search Query
        if (query.isNotEmpty()) {
            result = result.filter { it.title.contains(query, ignoreCase = true) }
        }

        // Apply Sorting
        when (sort) {
            SortOrder.NAME_ASC -> result.sortedBy { it.title.lowercase() }
            SortOrder.NAME_DESC -> result.sortedByDescending { it.title.lowercase() }
            SortOrder.DATE_ASC -> result.sortedBy { it.dateAdded }
            SortOrder.DATE_DESC -> result.sortedByDescending { it.dateAdded }
            SortOrder.SIZE_ASC -> result.sortedBy { it.size }
            SortOrder.SIZE_DESC -> result.sortedByDescending { it.size }
            SortOrder.DURATION_ASC -> result.sortedBy { it.duration }
            SortOrder.DURATION_DESC -> result.sortedByDescending { it.duration }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkVaultStatus()
    }

    fun triggerScan() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val videos = repository.scanLocalVideos(filterPrivate = true)
                _allVideos.value = videos
                _folders.value = repository.getFolders(videos)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }

    // Sort order
    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    // Set search query
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    // Select Folder
    fun selectFolder(folder: FolderItem?) {
        _selectedFolder.value = folder
    }

    // Playlist Methods
    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: VideoItem) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, video)
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, videoPath: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoPath)
        }
    }

    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideoCrossRef>> {
        return repository.getPlaylistVideosFlow(playlistId)
    }

    // Favorite Methods
    fun toggleFavorite(video: VideoItem) {
        viewModelScope.launch {
            if (video.isFavorite) {
                repository.removeFromFavorites(video.path)
            } else {
                repository.addToFavorites(video)
            }
            triggerScan()
        }
    }

    fun removeFavoriteByPath(path: String) {
        viewModelScope.launch {
            repository.removeFromFavorites(path)
            triggerScan()
        }
    }

    // History Methods
    fun addToHistory(video: VideoItem, position: Long) {
        viewModelScope.launch {
            repository.addToHistory(video, position)
        }
    }

    fun removeFromHistory(videoPath: String) {
        viewModelScope.launch {
            repository.removeFromHistory(videoPath)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // Vault Operations
    fun checkVaultStatus() {
        viewModelScope.launch {
            _isVaultConfigured.value = repository.isVaultConfigured()
        }
    }

    fun configureVault(pin: String) {
        viewModelScope.launch {
            repository.configureVaultPin(pin)
            _isVaultConfigured.value = true
            _isVaultUnlocked.value = true
            loadVaultVideos()
        }
    }

    fun unlockVault(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val verified = repository.verifyVaultPin(pin)
            if (verified) {
                _isVaultUnlocked.value = true
                loadVaultVideos()
            }
            onResult(verified)
        }
    }

    fun lockVault() {
        _isVaultUnlocked.value = false
        _vaultVideos.value = emptyList()
    }

    fun moveVideoToVault(video: VideoItem) {
        viewModelScope.launch {
            repository.addVideoToVault(video.path)
            if (video.isFavorite) {
                repository.removeFromFavorites(video.path)
            }
            triggerScan()
            if (_isVaultUnlocked.value) {
                loadVaultVideos()
            }
        }
    }

    fun restoreVideoFromVault(videoPath: String) {
        viewModelScope.launch {
            repository.removeVideoFromVault(videoPath)
            triggerScan()
            if (_isVaultUnlocked.value) {
                loadVaultVideos()
            }
        }
    }

    fun loadVaultVideos() {
        viewModelScope.launch {
            _vaultVideos.value = repository.scanVaultVideos()
        }
    }
}

class MPlayerViewModelFactory(
    private val application: Application,
    private val repository: VideoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MPlayerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MPlayerViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
