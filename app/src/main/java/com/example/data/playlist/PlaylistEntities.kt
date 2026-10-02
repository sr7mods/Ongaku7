package com.example.data.playlist

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val customArtPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "trackPath"])
    ]
)
data class PlaylistTrackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playlistId: Long,
    val trackPath: String,
    val orderIndex: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_stats")
data class TrackStatsEntity(
    @PrimaryKey
    val trackPath: String,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAt: Long = 0L
)
