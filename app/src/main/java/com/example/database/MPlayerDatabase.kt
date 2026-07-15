package com.example.database

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val videoPath: String,
    val title: String,
    val videoId: Long,
    val duration: Long,
    val size: Long,
    val folderName: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey val videoPath: String,
    val title: String,
    val videoId: Long,
    val duration: Long,
    val size: Long,
    val folderName: String,
    val lastPlayedPosition: Long,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val playlistId: Long = 0,
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_video_cross_ref", primaryKeys = ["playlistId", "videoPath"])
data class PlaylistVideoCrossRef(
    val playlistId: Long,
    val videoPath: String,
    val videoTitle: String,
    val videoId: Long,
    val duration: Long,
    val size: Long,
    val folderName: String
)

@Entity(tableName = "vault_settings")
data class VaultSettingEntity(
    @PrimaryKey val id: Int = 0,
    val pinCode: String
)

@Entity(tableName = "vault_videos")
data class VaultVideoEntity(
    @PrimaryKey val videoPath: String
)

@Entity(tableName = "network_streams")
data class NetworkStreamEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val dateAdded: Long = System.currentTimeMillis()
)

@Dao
interface MPlayerDao {
    // Network Streams
    @Query("SELECT * FROM network_streams ORDER BY dateAdded DESC")
    fun getNetworkStreams(): Flow<List<NetworkStreamEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkStream(stream: NetworkStreamEntity)

    @Query("DELETE FROM network_streams WHERE id = :id")
    suspend fun deleteNetworkStream(id: Long)

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY dateAdded DESC")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE videoPath = :videoPath")
    suspend fun deleteFavorite(videoPath: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE videoPath = :videoPath)")
    suspend fun isFavorite(videoPath: String): Boolean

    // History
    @Query("SELECT * FROM history ORDER BY lastPlayedTimestamp DESC")
    fun getHistory(): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    @Query("DELETE FROM history WHERE videoPath = :videoPath")
    suspend fun deleteHistory(videoPath: String)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("SELECT lastPlayedPosition FROM history WHERE videoPath = :videoPath")
    suspend fun getLastPosition(videoPath: String): Long?

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY dateCreated DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE playlistId = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE playlistId = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Playlist Videos
    @Query("SELECT * FROM playlist_video_cross_ref WHERE playlistId = :playlistId")
    fun getPlaylistVideos(playlistId: Long): Flow<List<PlaylistVideoCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideo(crossRef: PlaylistVideoCrossRef)

    @Query("DELETE FROM playlist_video_cross_ref WHERE playlistId = :playlistId AND videoPath = :videoPath")
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoPath: String)

    @Query("DELETE FROM playlist_video_cross_ref WHERE playlistId = :playlistId")
    suspend fun clearPlaylistVideos(playlistId: Long)

    // Vault
    @Query("SELECT * FROM vault_settings WHERE id = 0")
    suspend fun getVaultSetting(): VaultSettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveVaultSetting(setting: VaultSettingEntity)

    @Query("SELECT * FROM vault_videos")
    fun getVaultVideosFlow(): Flow<List<VaultVideoEntity>>

    @Query("SELECT * FROM vault_videos")
    suspend fun getVaultVideos(): List<VaultVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideoToVault(vaultVideo: VaultVideoEntity)

    @Query("DELETE FROM vault_videos WHERE videoPath = :videoPath")
    suspend fun removeVideoFromVault(videoPath: String)

    @Query("SELECT EXISTS(SELECT 1 FROM vault_videos WHERE videoPath = :videoPath)")
    suspend fun isVideoInVault(videoPath: String): Boolean
}

@Database(
    entities = [
        FavoriteEntity::class,
        HistoryEntity::class,
        PlaylistEntity::class,
        PlaylistVideoCrossRef::class,
        VaultSettingEntity::class,
        VaultVideoEntity::class,
        NetworkStreamEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class MPlayerDatabase : RoomDatabase() {
    abstract fun dao(): MPlayerDao

    companion object {
        @Volatile
        private var INSTANCE: MPlayerDatabase? = null

        fun getDatabase(context: Context): MPlayerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MPlayerDatabase::class.java,
                    "mplayer_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
