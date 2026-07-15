package com.example.models

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val resolution: String?,
    val mimeType: String?,
    val folderName: String,
    val dateAdded: Long,
    val isFavorite: Boolean = false,
    val lastPosition: Long = 0,
    val isPrivate: Boolean = false
) {
    val uri: Uri get() = Uri.parse("content://media/external/video/media/$id")
    
    val formattedDuration: String get() {
        if (duration <= 0) return "00:00"
        val totalSecs = duration / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    val formattedSize: String get() {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

data class FolderItem(
    val name: String,
    val path: String,
    val videoCount: Int,
    val isFavorite: Boolean = false
)
