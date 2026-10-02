package com.example.data.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // Tracks in Playlist
    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    fun getTracksForPlaylist(playlistId: Long): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY orderIndex ASC")
    suspend fun getTracksForPlaylistSync(playlistId: Long): List<PlaylistTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrack(track: PlaylistTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTracks(tracks: List<PlaylistTrackEntity>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackPath = :trackPath")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackPath: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackPath IN (:trackPaths)")
    suspend fun removeTracksFromPlaylist(playlistId: Long, trackPaths: List<String>)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylistTracks(playlistId: Long)

    @Update
    suspend fun updatePlaylistTracks(tracks: List<PlaylistTrackEntity>)

    // Track Stats (Favorites, Play Count)
    @Query("SELECT * FROM track_stats WHERE isFavorite = 1")
    fun getFavorites(): Flow<List<TrackStatsEntity>>

    @Query("SELECT * FROM track_stats WHERE trackPath = :trackPath")
    suspend fun getTrackStats(trackPath: String): TrackStatsEntity?

    @Query("SELECT * FROM track_stats WHERE playCount > 0 ORDER BY playCount DESC LIMIT 100")
    fun getMostPlayed(): Flow<List<TrackStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateTrackStats(stats: TrackStatsEntity)

    @Query("UPDATE track_stats SET isFavorite = :isFav WHERE trackPath = :trackPath")
    suspend fun setFavorite(trackPath: String, isFav: Boolean)

    @Query("SELECT isFavorite FROM track_stats WHERE trackPath = :trackPath")
    suspend fun isTrackFavorite(trackPath: String): Boolean?

    @Query("UPDATE playlist_tracks SET trackPath = :newPath WHERE trackPath = :oldPath")
    suspend fun updatePlaylistTrackPath(oldPath: String, newPath: String)

    @Query("UPDATE track_stats SET trackPath = :newPath WHERE trackPath = :oldPath")
    suspend fun updateTrackStatsPath(oldPath: String, newPath: String)

    @Query("DELETE FROM playlist_tracks WHERE trackPath = :trackPath")
    suspend fun deleteTrackFromAllPlaylists(trackPath: String)

    @Query("DELETE FROM track_stats WHERE trackPath = :trackPath")
    suspend fun deleteTrackStats(trackPath: String)
}
