package com.example.data.playlist

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlaylistRepository(context: Context) {

    private val playlistDao: PlaylistDao = AppDatabase.getDatabase(context).playlistDao()

    val allPlaylists: Flow<List<PlaylistEntity>> = playlistDao.getAllPlaylists()
    val favorites: Flow<List<TrackStatsEntity>> = playlistDao.getFavorites()
    val mostPlayed: Flow<List<TrackStatsEntity>> = playlistDao.getMostPlayed()

    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>> {
        return playlistDao.getTracksForPlaylist(playlistId)
    }

    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? = withContext(Dispatchers.IO) {
        playlistDao.getPlaylistById(playlistId)
    }

    suspend fun createPlaylist(
        title: String,
        description: String = "",
        customArtPath: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val entity = PlaylistEntity(
            title = title.trim(),
            description = description.trim(),
            customArtPath = customArtPath,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        playlistDao.insertPlaylist(entity)
    }

    suspend fun updatePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        playlistDao.clearPlaylistTracks(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackPath: String): Boolean = withContext(Dispatchers.IO) {
        val existing = playlistDao.getTracksForPlaylistSync(playlistId)
        if (existing.any { it.trackPath == trackPath }) {
            return@withContext false // already in playlist
        }
        val nextOrder = if (existing.isEmpty()) 0 else (existing.maxOf { it.orderIndex } + 1)
        playlistDao.insertPlaylistTrack(
            PlaylistTrackEntity(
                playlistId = playlistId,
                trackPath = trackPath,
                orderIndex = nextOrder
            )
        )
        // Update playlist's updatedAt timestamp
        playlistDao.getPlaylistById(playlistId)?.let { p ->
            playlistDao.updatePlaylist(p.copy(updatedAt = System.currentTimeMillis()))
        }
        return@withContext true
    }

    suspend fun addTracksToPlaylist(playlistId: Long, trackPaths: List<String>): Int = withContext(Dispatchers.IO) {
        val existing = playlistDao.getTracksForPlaylistSync(playlistId)
        val existingPaths = existing.map { it.trackPath }.toSet()
        var nextOrder = if (existing.isEmpty()) 0 else (existing.maxOf { it.orderIndex } + 1)
        var addedCount = 0

        val newEntities = mutableListOf<PlaylistTrackEntity>()
        for (path in trackPaths) {
            if (!existingPaths.contains(path)) {
                newEntities.add(
                    PlaylistTrackEntity(
                        playlistId = playlistId,
                        trackPath = path,
                        orderIndex = nextOrder++
                    )
                )
                addedCount++
            }
        }
        if (newEntities.isNotEmpty()) {
            playlistDao.insertPlaylistTracks(newEntities)
            playlistDao.getPlaylistById(playlistId)?.let { p ->
                playlistDao.updatePlaylist(p.copy(updatedAt = System.currentTimeMillis()))
            }
        }
        return@withContext addedCount
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackPath: String) = withContext(Dispatchers.IO) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackPath)
        normalizeTrackOrder(playlistId)
    }

    suspend fun removeTracksFromPlaylist(playlistId: Long, trackPaths: List<String>) = withContext(Dispatchers.IO) {
        playlistDao.removeTracksFromPlaylist(playlistId, trackPaths)
        normalizeTrackOrder(playlistId)
    }

    suspend fun reorderTracks(playlistId: Long, orderedTrackPaths: List<String>) = withContext(Dispatchers.IO) {
        val currentTracks = playlistDao.getTracksForPlaylistSync(playlistId)
        val mapByPath = currentTracks.associateBy { it.trackPath }
        val updated = orderedTrackPaths.mapIndexedNotNull { index, path ->
            mapByPath[path]?.copy(orderIndex = index)
        }
        playlistDao.updatePlaylistTracks(updated)
        playlistDao.getPlaylistById(playlistId)?.let { p ->
            playlistDao.updatePlaylist(p.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun moveTrack(playlistId: Long, fromIndex: Int, toIndex: Int) = withContext(Dispatchers.IO) {
        val tracks = playlistDao.getTracksForPlaylistSync(playlistId).toMutableList()
        if (fromIndex in tracks.indices && toIndex in tracks.indices && fromIndex != toIndex) {
            val moved = tracks.removeAt(fromIndex)
            tracks.add(toIndex, moved)
            val updated = tracks.mapIndexed { idx, item -> item.copy(orderIndex = idx) }
            playlistDao.updatePlaylistTracks(updated)
        }
    }

    private suspend fun normalizeTrackOrder(playlistId: Long) {
        val remaining = playlistDao.getTracksForPlaylistSync(playlistId)
        val updated = remaining.mapIndexed { index, item -> item.copy(orderIndex = index) }
        playlistDao.updatePlaylistTracks(updated)
        playlistDao.getPlaylistById(playlistId)?.let { p ->
            playlistDao.updatePlaylist(p.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    // Smart Playlist Stats
    suspend fun recordTrackPlayed(trackPath: String) = withContext(Dispatchers.IO) {
        val existing = playlistDao.getTrackStats(trackPath)
        val updated = if (existing != null) {
            existing.copy(
                playCount = existing.playCount + 1,
                lastPlayedAt = System.currentTimeMillis()
            )
        } else {
            TrackStatsEntity(
                trackPath = trackPath,
                isFavorite = false,
                playCount = 1,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
        playlistDao.insertOrUpdateTrackStats(updated)
    }

    suspend fun toggleFavorite(trackPath: String): Boolean = withContext(Dispatchers.IO) {
        val existing = playlistDao.getTrackStats(trackPath)
        val newFav = if (existing != null) !existing.isFavorite else true
        val updated = if (existing != null) {
            existing.copy(isFavorite = newFav)
        } else {
            TrackStatsEntity(
                trackPath = trackPath,
                isFavorite = true,
                playCount = 0,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
        playlistDao.insertOrUpdateTrackStats(updated)
        return@withContext newFav
    }

    suspend fun isFavorite(trackPath: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext playlistDao.isTrackFavorite(trackPath) ?: false
    }

    suspend fun updateTrackPath(oldPath: String, newPath: String) = withContext(Dispatchers.IO) {
        playlistDao.updatePlaylistTrackPath(oldPath, newPath)
        playlistDao.updateTrackStatsPath(oldPath, newPath)
    }

    suspend fun deleteTrackFromAllPlaylists(trackPath: String) = withContext(Dispatchers.IO) {
        playlistDao.deleteTrackFromAllPlaylists(trackPath)
    }

    suspend fun deleteTrackStats(trackPath: String) = withContext(Dispatchers.IO) {
        playlistDao.deleteTrackStats(trackPath)
    }
}
