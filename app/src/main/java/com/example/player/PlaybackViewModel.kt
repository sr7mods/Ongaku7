package com.example.player

import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.data.AudioStorageScanner
import com.example.data.AudioTrack
import com.example.data.FolderNode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import com.example.data.playlist.PlaylistRepository
import com.example.data.playlist.PlaylistEntity
import com.example.data.playlist.PlaylistTrackEntity
import com.example.data.playlist.TrackStatsEntity
import com.example.data.playlist.SmartPlaylistType

import androidx.media3.common.PlaybackParameters
import android.media.audiofx.PresetReverb
import android.media.audiofx.BassBoost
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink

enum class IconType {
    OUTLINED, FILLED, ROUNDED, CYBERPUNK
}

enum class AlbumArtType {
    ROUNDED, ROTATING_VINYL, FLOATING_CARD, AMBIENT_GLOW
}

enum class RepeatMode {
    OFF,     // STATE 1: NORMAL (Stop at end)
    ALL,     // STATE 2: LOOP ALL (Repeat Queue)
    ONE,     // STATE 3: LOOP ONE (Repeat Current Track)
    SHUFFLE  // STATE 4: SHUFFLE (Randomized Playback)
}

enum class ReverbSystem {
    ROOM, HZ
}

enum class SortType {
    TIME, TYPE, ALPHABET
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private var exoPlayer: ExoPlayer? = null
    var mediaSession: MediaSession? = null
        private set
    private var presetReverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private var eightDAudioProcessor: EightDAudioProcessor? = null

    private val prefs = application.getSharedPreferences("player_preferences", Context.MODE_PRIVATE)

    // UI customizer states
    private val _iconType = MutableStateFlow(IconType.CYBERPUNK)
    val iconType: StateFlow<IconType> = _iconType.asStateFlow()

    private val _albumArtType = MutableStateFlow(AlbumArtType.ROUNDED)
    val albumArtType: StateFlow<AlbumArtType> = _albumArtType.asStateFlow()

    // Sorting states
    private val _sortType = MutableStateFlow(SortType.TIME)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DESCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private var rawFolders: List<FolderNode> = emptyList()

    // Scanning states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderNode>>(emptyList())
    val folders: StateFlow<List<FolderNode>> = _folders.asStateFlow()

    // Folder navigation state
    private val _currentFolder = MutableStateFlow<FolderNode?>(null)
    val currentFolder: StateFlow<FolderNode?> = _currentFolder.asStateFlow()

    private val _folderNavigationStack = MutableStateFlow<List<FolderNode>>(emptyList())
    val folderNavigationStack: StateFlow<List<FolderNode>> = _folderNavigationStack.asStateFlow()

    // Expanded folders tracking
    private val _expandedFolderPaths = MutableStateFlow<Set<String>>(emptySet())
    val expandedFolderPaths: StateFlow<Set<String>> = _expandedFolderPaths.asStateFlow()

    // Playback states
    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackQueue = MutableStateFlow<List<AudioTrack>>(emptyList())
    val playbackQueue: StateFlow<List<AudioTrack>> = _playbackQueue.asStateFlow()

    // Queue boundary notifications ("Reached at the top" / "Reached at the bottom")
    private val _queueBoundaryEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val queueBoundaryEvent: SharedFlow<String> = _queueBoundaryEvent.asSharedFlow()

    fun triggerQueueBoundary(message: String) {
        _queueBoundaryEvent.tryEmit(message)
    }

    // Lyrics Engine States
    private val _currentLyrics = MutableStateFlow<com.example.player.lyrics.LyricsResult?>(null)
    val currentLyrics: StateFlow<com.example.player.lyrics.LyricsResult?> = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    // Dynamic App Icon Switcher State
    private val _activeAppIcon = MutableStateFlow(AppIconManager.getActiveIconId(application))
    val activeAppIcon: StateFlow<String> = _activeAppIcon.asStateFlow()

    fun setAppIcon(iconId: String): Boolean {
        val success = AppIconManager.setAppIcon(getApplication(), iconId)
        if (success) {
            _activeAppIcon.value = iconId
        }
        return success
    }

    fun loadLyricsForCurrentTrack() {
        val track = _currentTrack.value
        if (track == null) {
            _currentLyrics.value = null
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingLyrics.value = true
            val lyrics = com.example.player.lyrics.LyricsParser.loadLyricsForAudio(track.path)
            _currentLyrics.value = lyrics
            _isLoadingLyrics.value = false
        }
    }

    // Audio options states
    private val _tempo = MutableStateFlow(1.0f)
    val tempo: StateFlow<Float> = _tempo.asStateFlow()

    private val _pitch = MutableStateFlow(1.0f)
    val pitch: StateFlow<Float> = _pitch.asStateFlow()

    private val _reverbLevel = MutableStateFlow(0f)
    val reverbLevel: StateFlow<Float> = _reverbLevel.asStateFlow()

    private val _reverbSystem = MutableStateFlow(ReverbSystem.ROOM)
    val reverbSystem: StateFlow<ReverbSystem> = _reverbSystem.asStateFlow()

    private val _reverbHz = MutableStateFlow(0f)
    val reverbHz: StateFlow<Float> = _reverbHz.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0f)
    val bassBoostStrength: StateFlow<Float> = _bassBoostStrength.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // A/B loop points
    private val _loopStartMs = MutableStateFlow<Long?>(null)
    val loopStartMs: StateFlow<Long?> = _loopStartMs.asStateFlow()

    private val _loopEndMs = MutableStateFlow<Long?>(null)
    val loopEndMs: StateFlow<Long?> = _loopEndMs.asStateFlow()

    // Set of track paths with removed artwork
    private val _removedArtTrackPaths = MutableStateFlow<Set<String>>(emptySet())
    val removedArtTrackPaths: StateFlow<Set<String>> = _removedArtTrackPaths.asStateFlow()

    // Hidden and NoMedia setting
    private val _showHiddenFiles = MutableStateFlow(false)
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    // Scroll to last played setting
    private val _scrollToLastPlayed = MutableStateFlow(false)
    val scrollToLastPlayed: StateFlow<Boolean> = _scrollToLastPlayed.asStateFlow()

    private val _lastPlayedTrackPath = MutableStateFlow("")
    val lastPlayedTrackPath: StateFlow<String> = _lastPlayedTrackPath.asStateFlow()

    // Equalizer States
    private val _eqEnabled = MutableStateFlow(false)
    val eqEnabled: StateFlow<Boolean> = _eqEnabled.asStateFlow()

    private val _eqBands = MutableStateFlow(listOf(0f, 0f, 0f, 0f, 0f)) // 5 bands (60Hz, 230Hz, 910Hz, 4kHz, 14kHz)
    val eqBands: StateFlow<List<Float>> = _eqBands.asStateFlow()

    // 8D Audio States (WebAudio API LFO Architecture)
    private val _eightDEnabled = MutableStateFlow(false)
    val eightDEnabled: StateFlow<Boolean> = _eightDEnabled.asStateFlow()

    private val _eightDSpeed = MutableStateFlow(0.15f) // 0.01Hz to 1.0Hz (Classic preset default: 0.15 Hz)
    val eightDSpeed: StateFlow<Float> = _eightDSpeed.asStateFlow()

    private val _eightDDepth = MutableStateFlow(90f) // 0% to 100% (Classic preset default: 90%)
    val eightDDepth: StateFlow<Float> = _eightDDepth.asStateFlow()

    private val _eightDPreset = MutableStateFlow("classic")
    val eightDPreset: StateFlow<String> = _eightDPreset.asStateFlow()

    private val noisyAndBluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    Log.d("PlaybackVM", "ACTION_AUDIO_BECOMING_NOISY received -> pausing")
                    pause()
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    Log.d("PlaybackVM", "Bluetooth device disconnected -> pausing")
                    pause()
                }
            }
        }
    }

    // File Manager / Multi-select States
    private val _selectedPaths = MutableStateFlow<Set<String>>(emptySet())
    val selectedPaths: StateFlow<Set<String>> = _selectedPaths.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    // Playlist System
    val playlistRepository = PlaylistRepository(application)

    val playlists: StateFlow<List<PlaylistEntity>> = playlistRepository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteStats: StateFlow<List<TrackStatsEntity>> = playlistRepository.favorites
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedStats: StateFlow<List<TrackStatsEntity>> = playlistRepository.mostPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var progressJob: Job? = null
    private var panningJob: Job? = null
    private var androidEqualizer: android.media.audiofx.Equalizer? = null
    private var androidVirtualizer: android.media.audiofx.Virtualizer? = null

    init {
        instance = this
        
        try {
            _iconType.value = IconType.valueOf(
                prefs.getString("icon_type", IconType.CYBERPUNK.name) ?: IconType.CYBERPUNK.name
            )
        } catch (e: Exception) {
            _iconType.value = IconType.CYBERPUNK
        }
        
        try {
            val savedArtType = prefs.getString("album_art_type", AlbumArtType.ROUNDED.name) ?: AlbumArtType.ROUNDED.name
            _albumArtType.value = when (savedArtType) {
                "SQUARE" -> AlbumArtType.ROUNDED
                "VINYL" -> AlbumArtType.ROTATING_VINYL
                else -> AlbumArtType.valueOf(savedArtType)
            }
        } catch (e: Exception) {
            _albumArtType.value = AlbumArtType.ROUNDED
        }
        
        try {
            _repeatMode.value = RepeatMode.valueOf(
                prefs.getString("repeat_mode", RepeatMode.OFF.name) ?: RepeatMode.OFF.name
            )
        } catch (e: Exception) {
            _repeatMode.value = RepeatMode.OFF
        }
        
        try {
            _sortType.value = SortType.valueOf(
                prefs.getString("sort_type", SortType.TIME.name) ?: SortType.TIME.name
            )
        } catch (e: Exception) {
            _sortType.value = SortType.TIME
        }
        
        try {
            _sortOrder.value = SortOrder.valueOf(
                prefs.getString("sort_order", SortOrder.DESCENDING.name) ?: SortOrder.DESCENDING.name
            )
        } catch (e: Exception) {
            _sortOrder.value = SortOrder.DESCENDING
        }

        try {
            val savedRemovedArt = prefs.getStringSet("removed_art_paths", emptySet()) ?: emptySet()
            _removedArtTrackPaths.value = savedRemovedArt
        } catch (e: Exception) {
            // fallback
        }

        try {
            _showHiddenFiles.value = prefs.getBoolean("show_hidden_files", false)
            _scrollToLastPlayed.value = prefs.getBoolean("scroll_to_last_played", false)
            _lastPlayedTrackPath.value = prefs.getString("last_played_track", "") ?: ""
            
            _eqEnabled.value = prefs.getBoolean("eq_enabled", false)
            val b0 = prefs.getFloat("eq_band_0", 0f)
            val b1 = prefs.getFloat("eq_band_1", 0f)
            val b2 = prefs.getFloat("eq_band_2", 0f)
            val b3 = prefs.getFloat("eq_band_3", 0f)
            val b4 = prefs.getFloat("eq_band_4", 0f)
            _eqBands.value = listOf(b0, b1, b2, b3, b4)
            
            _eightDEnabled.value = prefs.getBoolean("eight_d_enabled", false)
            _eightDSpeed.value = prefs.getFloat("eight_d_speed", 0.15f)
            
            val savedReverbSys = prefs.getString("reverb_system", ReverbSystem.ROOM.name) ?: ReverbSystem.ROOM.name
            _reverbSystem.value = try { ReverbSystem.valueOf(savedReverbSys) } catch (e: Exception) { ReverbSystem.ROOM }
            _reverbHz.value = prefs.getFloat("reverb_hz", 0f)
            _bassBoostStrength.value = prefs.getFloat("bass_boost_strength", 0f)
            _eightDDepth.value = prefs.getFloat("eight_d_depth", 90f)
            _eightDPreset.value = prefs.getString("eight_d_preset", "classic") ?: "classic"
        } catch (e: Exception) {
            // fallback
        }

        try {
            val filter = IntentFilter().apply {
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(noisyAndBluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                application.registerReceiver(noisyAndBluetoothReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error registering receiver: ${e.message}")
        }
        
        initializePlayer()
        if (_eightDEnabled.value) {
            updateSpatialEffect()
        }
        // Always rescan storage on complete launch
        scanAudio(force = true)
    }

    private fun persistSetting(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private fun triggerNotificationUpdate() {
        _currentTrack.value?.let { track ->
            MediaPlaybackService.updateNotification(
                getApplication(),
                track,
                _isPlaying.value,
                _currentPosition.value,
                _duration.value,
                _repeatMode.value
            )
        }
        com.example.widget.PlayerWidgetProvider.updateAllWidgets(getApplication())
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun initializePlayer() {
        try {
            val context = getApplication<Application>()
            val proc = EightDAudioProcessor().apply {
                enabled = _eightDEnabled.value
                speed = _eightDSpeed.value
                depth = _eightDDepth.value
            }
            eightDAudioProcessor = proc

            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): androidx.media3.exoplayer.audio.AudioSink {
                    return DefaultAudioSink.Builder(context)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(proc))
                        .build()
                }
            }.apply {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setEnableAudioTrackPlaybackParams(true)
            }

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 50_000,
                    /* maxBufferMs = */ 120_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBackBuffer(30_000, true)
                .build()

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build()

            exoPlayer = ExoPlayer.Builder(context, renderersFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                            if (isPlaying) {
                                startProgressPolling()
                                applyReverb()
                                applyBassBoost()
                                applyEqualizerSettings()
                                applyVirtualizerSettings()
                            } else {
                                stopProgressPolling()
                            }
                            triggerNotificationUpdate()
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                _duration.value = duration
                                applyReverb()
                                applyBassBoost()
                                applyEqualizerSettings()
                                applyVirtualizerSettings()
                            } else if (state == Player.STATE_ENDED) {
                                playNextTrack()
                            }
                            triggerNotificationUpdate()
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            super.onMediaItemTransition(mediaItem, reason)
                            val path = mediaItem?.mediaId
                            if (path != null) {
                                // Find corresponding AudioTrack in queue or all folders
                                val track = findTrackByPath(path)
                                if (track != null) {
                                    _currentTrack.value = track
                                }
                            }
                            // Apply pitch/tempo parameters and reverb for new item
                            updatePlaybackParameters()
                            applyReverb()
                            applyBassBoost()
                            triggerNotificationUpdate()
                        }
                    })
                }

            exoPlayer?.let { player ->
                val intent = Intent(context, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                val pendingIntent = android.app.PendingIntent.getActivity(
                    context,
                    99,
                    intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                mediaSession = MediaSession.Builder(context, player)
                    .setSessionActivity(pendingIntent)
                    .setCallback(object : MediaSession.Callback {
                        override fun onConnect(
                            session: MediaSession,
                            controller: MediaSession.ControllerInfo
                        ): MediaSession.ConnectionResult {
                            val connectionResult = super.onConnect(session, controller)
                            val availableSessionCommands = connectionResult.availableSessionCommands.buildUpon()
                            val availablePlayerCommands = connectionResult.availablePlayerCommands.buildUpon()
                                .addAll(
                                    Player.COMMAND_PLAY_PAUSE,
                                    Player.COMMAND_PREPARE,
                                    Player.COMMAND_STOP,
                                    Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
                                    Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                                    Player.COMMAND_SEEK_TO_PREVIOUS,
                                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                                    Player.COMMAND_SEEK_TO_NEXT,
                                    Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                                    Player.COMMAND_SEEK_BACK,
                                    Player.COMMAND_SEEK_FORWARD,
                                    Player.COMMAND_SET_REPEAT_MODE,
                                    Player.COMMAND_SET_SHUFFLE_MODE,
                                    Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                                    Player.COMMAND_GET_TIMELINE
                                )
                                .build()
                            return MediaSession.ConnectionResult.accept(
                                availableSessionCommands.build(),
                                availablePlayerCommands
                            )
                        }
                    })
                    .build()
                applyRepeatAndShuffleMode(_repeatMode.value)
            }
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error initializing ExoPlayer: ${e.message}")
        }
    }

    fun setIconType(type: IconType) {
        _iconType.value = type
        persistSetting("icon_type", type.name)
    }

    fun setAlbumArtType(type: AlbumArtType) {
        _albumArtType.value = type
        persistSetting("album_art_type", type.name)
    }

    fun setSortType(type: SortType) {
        _sortType.value = type
        persistSetting("sort_type", type.name)
        applySorting()
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
        persistSetting("sort_order", order.name)
        applySorting()
    }

    fun setTempo(value: Float) {
        _tempo.value = value
        updatePlaybackParameters()
    }

    fun setPitch(value: Float) {
        _pitch.value = value
        updatePlaybackParameters()
    }

    private fun updatePlaybackParameters() {
        try {
            exoPlayer?.playbackParameters = PlaybackParameters(_tempo.value, _pitch.value)
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error setting playback parameters: ${e.message}")
        }
    }

    fun setReverbLevel(level: Float) {
        _reverbLevel.value = level
        persistSetting("reverb_level", level.toString())
        applyReverb()
    }

    fun setReverbSystem(system: ReverbSystem) {
        _reverbSystem.value = system
        persistSetting("reverb_system", system.name)
        applyReverb()
    }

    fun setReverbHz(hz: Float) {
        _reverbHz.value = hz
        persistSetting("reverb_hz", hz.toString())
        applyReverb()
    }

    fun setBassBoostStrength(strength: Float) {
        _bassBoostStrength.value = strength
        persistSetting("bass_boost_strength", strength.toString())
        applyBassBoost()
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun applyBassBoost() {
        exoPlayer?.audioSessionId?.let { sessionId ->
            if (sessionId != android.media.AudioManager.AUDIO_SESSION_ID_GENERATE) {
                try {
                    if (bassBoost == null || bassBoost?.id != sessionId) {
                        try {
                            bassBoost?.release()
                        } catch (e: Exception) {}
                        bassBoost = BassBoost(0, sessionId).apply {
                            enabled = true
                        }
                    }
                    bassBoost?.let { bb ->
                        bb.enabled = _bassBoostStrength.value > 0f
                        if (bb.strengthSupported) {
                            bb.setStrength(_bassBoostStrength.value.toInt().coerceIn(0, 1000).toShort())
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackVM", "Error applying BassBoost: ${e.message}")
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyReverb() {
        exoPlayer?.audioSessionId?.let { sessionId ->
            if (sessionId != android.media.AudioManager.AUDIO_SESSION_ID_GENERATE) {
                try {
                    if (presetReverb == null) {
                        presetReverb = PresetReverb(0, sessionId).apply {
                            enabled = true
                        }
                    }
                    if (_reverbSystem.value == ReverbSystem.ROOM) {
                        val presetIndex = _reverbLevel.value.toInt().coerceIn(0, 6).toShort()
                        presetReverb?.preset = presetIndex
                    } else {
                        val hzVal = _reverbHz.value
                        val presetIndex = when {
                            hzVal <= 0f -> 0.toShort()
                            hzVal < 300f -> 1.toShort()
                            hzVal < 800f -> 2.toShort()
                            hzVal < 2000f -> 3.toShort()
                            hzVal < 4500f -> 4.toShort()
                            hzVal < 7500f -> 5.toShort()
                            else -> 6.toShort()
                        }
                        presetReverb?.preset = presetIndex
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackVM", "Error applying PresetReverb: ${e.message}")
                }
            }
        }
    }

    private fun applyRepeatAndShuffleMode(mode: RepeatMode) {
        exoPlayer?.let { player ->
            when (mode) {
                RepeatMode.OFF -> {
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.shuffleModeEnabled = false
                }
                RepeatMode.ALL -> {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.shuffleModeEnabled = false
                }
                RepeatMode.ONE -> {
                    player.repeatMode = Player.REPEAT_MODE_ONE
                    player.shuffleModeEnabled = false
                }
                RepeatMode.SHUFFLE -> {
                    player.repeatMode = Player.REPEAT_MODE_ALL
                    player.shuffleModeEnabled = true
                }
            }
        }
    }

    fun toggleRepeatMode() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.SHUFFLE
            RepeatMode.SHUFFLE -> RepeatMode.OFF
        }
        setRepeatMode(next, showToast = true)
    }

    fun setRepeatMode(mode: RepeatMode, showToast: Boolean = false) {
        _repeatMode.value = mode
        applyRepeatAndShuffleMode(mode)
        persistSetting("repeat_mode", mode.name)
        val toastNotice = when (mode) {
            RepeatMode.OFF -> "Mode: Normal (Stop at end)"
            RepeatMode.ALL -> "Mode: Loop All"
            RepeatMode.ONE -> "Mode: Loop Current Track"
            RepeatMode.SHUFFLE -> "Mode: Shuffle (Random)"
        }
        if (showToast) {
            try {
                android.widget.Toast.makeText(getApplication(), toastNotice, android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Toast error: ${e.message}")
            }
        }
        _queueBoundaryEvent.tryEmit(toastNotice)
        triggerNotificationUpdate()
    }

    fun toggleABLoop() {
        val start = _loopStartMs.value
        val end = _loopEndMs.value
        val currentPos = _currentPosition.value

        when {
            start == null -> {
                _loopStartMs.value = currentPos
            }
            end == null -> {
                if (currentPos > start) {
                    _loopEndMs.value = currentPos
                } else {
                    _loopEndMs.value = start
                    _loopStartMs.value = currentPos
                }
            }
            else -> {
                _loopStartMs.value = null
                _loopEndMs.value = null
            }
        }
    }

    fun isArtRemoved(path: String): Boolean {
        return _removedArtTrackPaths.value.contains(path)
    }

    fun getPersistedTrackMetadata(track: AudioTrack): AudioTrack {
        val title = prefs.getString("meta_title_${track.path}", null) ?: track.title
        val artist = prefs.getString("meta_artist_${track.path}", null) ?: track.artist
        val album = prefs.getString("meta_album_${track.path}", null) ?: track.album
        val genre = prefs.getString("meta_genre_${track.path}", null) ?: track.genre
        val year = prefs.getString("meta_year_${track.path}", null) ?: track.year
        val trackNumber = prefs.getString("meta_track_number_${track.path}", null) ?: track.trackNumber
        val discNumber = prefs.getString("meta_disc_number_${track.path}", null) ?: track.discNumber
        val comment = prefs.getString("meta_comment_${track.path}", null) ?: track.comment
        val customArtPath = prefs.getString("meta_custom_art_${track.path}", null)
        
        return track.copy(
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            year = year,
            trackNumber = trackNumber,
            discNumber = discNumber,
            comment = comment,
            customArtPath = customArtPath
        )
    }

    private fun applyPersistedMetadataToFolders(folders: List<FolderNode>): List<FolderNode> {
        return folders.map { node ->
            val updatedTracks = node.tracks.map { getPersistedTrackMetadata(it) }.toMutableList()
            val updatedSubs = applyPersistedMetadataToFolders(node.subFolders).toMutableList()
            node.copy(tracks = updatedTracks, subFolders = updatedSubs)
        }
    }

    fun updateTrackMetadata(
        trackPath: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newYear: String,
        newTrackNumber: String,
        newDiscNumber: String,
        newComment: String,
        newCustomArtPath: String?,
        removeArt: Boolean
    ) {
        prefs.edit().apply {
            putString("meta_title_$trackPath", newTitle)
            putString("meta_artist_$trackPath", newArtist)
            putString("meta_album_$trackPath", newAlbum)
            putString("meta_genre_$trackPath", newGenre)
            putString("meta_year_$trackPath", newYear)
            putString("meta_track_number_$trackPath", newTrackNumber)
            putString("meta_disc_number_$trackPath", newDiscNumber)
            putString("meta_comment_$trackPath", newComment)
            if (newCustomArtPath != null) {
                putString("meta_custom_art_$trackPath", newCustomArtPath)
            } else {
                remove("meta_custom_art_$trackPath")
            }
            apply()
        }

        if (removeArt) {
            _removedArtTrackPaths.update { it + trackPath }
            val currentSet = prefs.getStringSet("removed_art_paths", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("removed_art_paths", currentSet + trackPath).apply()
        } else {
            _removedArtTrackPaths.update { it - trackPath }
            val currentSet = prefs.getStringSet("removed_art_paths", emptySet()) ?: emptySet()
            prefs.edit().putStringSet("removed_art_paths", currentSet - trackPath).apply()
        }

        // Write tags to the actual file on storage if it is an MP3 file
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val origFile = java.io.File(trackPath)
                if (origFile.exists() && trackPath.endsWith(".mp3", ignoreCase = true)) {
                    val mp3file = com.mpatric.mp3agic.Mp3File(trackPath)
                    val id3v2Tag: com.mpatric.mp3agic.ID3v2 = if (mp3file.hasId3v2Tag()) {
                        mp3file.id3v2Tag
                    } else {
                        val newTag = com.mpatric.mp3agic.ID3v24Tag()
                        mp3file.id3v2Tag = newTag
                        newTag
                    }
                    
                    id3v2Tag.title = newTitle
                    id3v2Tag.artist = newArtist
                    id3v2Tag.album = newAlbum
                    id3v2Tag.genreDescription = newGenre
                    id3v2Tag.year = newYear
                    id3v2Tag.track = newTrackNumber
                    id3v2Tag.comment = newComment
                    
                    if (newCustomArtPath != null) {
                        val artFile = java.io.File(newCustomArtPath)
                        if (artFile.exists()) {
                            val bytes = artFile.readBytes()
                            val mimeType = if (newCustomArtPath.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"
                            id3v2Tag.setAlbumImage(bytes, mimeType)
                        }
                    } else if (removeArt) {
                        id3v2Tag.clearAlbumImage()
                    }
                    
                    val tempPath = trackPath + ".tmp"
                    mp3file.save(tempPath)
                    
                    val tempFile = java.io.File(tempPath)
                    if (tempFile.exists()) {
                        if (origFile.delete()) {
                            tempFile.renameTo(origFile)
                            Log.d("PlaybackVM", "Successfully updated physical ID3v2 tags of file: $trackPath")

                            // Immediately rescan via MediaScannerConnection
                            MediaScannerConnection.scanFile(
                                getApplication(),
                                arrayOf(origFile.absolutePath),
                                null
                            ) { path, uri ->
                                Log.d("PlaybackVM", "MediaScanner rescan complete for metadata update: $path -> $uri")
                            }

                            // Also update MediaStore audio metadata fields
                            try {
                                val resolver = getApplication<Application>().contentResolver
                                val values = ContentValues().apply {
                                    put(MediaStore.Audio.Media.TITLE, newTitle)
                                    put(MediaStore.Audio.Media.ARTIST, newArtist)
                                    put(MediaStore.Audio.Media.ALBUM, newAlbum)
                                    put(MediaStore.Audio.Media.YEAR, newYear.toIntOrNull() ?: 0)
                                }
                                resolver.update(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    values,
                                    "${MediaStore.Audio.Media.DATA} = ?",
                                    arrayOf(origFile.absolutePath)
                                )
                            } catch (e: Exception) {
                                Log.w("PlaybackVM", "MediaStore update on metadata write: ${e.message}")
                            }
                        } else {
                            tempFile.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Error writing tags to MP3 file: ${e.message}", e)
            }
        }

        rawFolders = applyPersistedMetadataToFolders(rawFolders)
        applySorting()

        _currentTrack.value?.let { current ->
            if (current.path == trackPath) {
                _currentTrack.value = getPersistedTrackMetadata(current)
            }
        }
    }

    fun renameTrackFile(track: AudioTrack, newFileName: String): Boolean {
        try {
            val file = File(track.path)
            val parentDir = file.parentFile ?: return false
            val extension = file.extension
            val cleanName = if (newFileName.endsWith(".$extension", ignoreCase = true)) {
                newFileName
            } else {
                "$newFileName.$extension"
            }
            val destination = File(parentDir, cleanName)
            val oldPath = track.path
            val newPath = destination.absolutePath

            if (file.renameTo(destination)) {
                val updatedTrack = track.copy(path = newPath, title = destination.nameWithoutExtension)

                if (_currentTrack.value?.path == oldPath) {
                    _currentTrack.value = updatedTrack
                }

                _playbackQueue.update { queue ->
                    queue.map { t ->
                        if (t.path == oldPath) updatedTrack else t
                    }
                }

                _folders.update { currentFolders ->
                    currentFolders.map { f -> renameTrackInFolderRecursive(f, oldPath, updatedTrack) }
                }

                // Update Room playlist & favorite database
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        playlistRepository.updateTrackPath(oldPath, newPath)
                    } catch (e: Exception) {
                        Log.e("PlaybackVM", "Error updating playlist track path: ${e.message}")
                    }
                }

                // Immediate MediaScannerConnection scan & MediaStore update
                try {
                    MediaScannerConnection.scanFile(
                        getApplication(),
                        arrayOf(oldPath, newPath),
                        null
                    ) { path, uri ->
                        Log.d("PlaybackVM", "MediaScanner scan complete for rename: $path -> $uri")
                    }

                    val resolver = getApplication<Application>().contentResolver
                    val values = ContentValues().apply {
                        put(MediaStore.Audio.Media.DATA, newPath)
                        put(MediaStore.Audio.Media.DISPLAY_NAME, destination.name)
                        put(MediaStore.Audio.Media.TITLE, destination.nameWithoutExtension)
                    }
                    resolver.update(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        values,
                        "${MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(oldPath)
                    )
                } catch (e: Exception) {
                    Log.w("PlaybackVM", "MediaStore update on rename: ${e.message}")
                }

                exoPlayer?.let { player ->
                    if (player.currentMediaItem?.mediaId == oldPath) {
                        val wasPlaying = player.isPlaying
                        val currentPos = player.currentPosition
                        player.stop()
                        player.clearMediaItems()
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.fromFile(destination))
                            .setMediaId(newPath)
                            .build()
                        player.setMediaItem(mediaItem)
                        player.prepare()
                        player.seekTo(currentPos)
                        if (wasPlaying) {
                            player.play()
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error renaming file: ${e.message}")
        }
        return false
    }

    private fun renameTrackInFolderRecursive(node: FolderNode, oldPath: String, updatedTrack: AudioTrack): FolderNode {
        val updatedTracks = node.tracks.map { t ->
            if (t.path == oldPath) updatedTrack else t
        }.toMutableList()

        val updatedSubfolders = node.subFolders.map { sub ->
            renameTrackInFolderRecursive(sub, oldPath, updatedTrack)
        }.toMutableList()

        return node.copy(tracks = updatedTracks, subFolders = updatedSubfolders)
    }

    fun scanAudio(force: Boolean = false) {
        if (!force && rawFolders.isNotEmpty()) {
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val scanned = AudioStorageScanner.scanAudioFiles(getApplication(), _showHiddenFiles.value)
                rawFolders = applyPersistedMetadataToFolders(scanned)
                applySorting()
                resetFolderNavigation()
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Error scanning audio: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun applySorting() {
        val sorted = sortFoldersAndTracks(rawFolders, _sortType.value, _sortOrder.value)
        _folders.value = sorted
        
        val current = _currentFolder.value
        if (current != null) {
            _currentFolder.value = findFolderInListRecursive(sorted, current.path)
        }
        
        _folderNavigationStack.update { stack ->
            stack.map { node -> findFolderInListRecursive(sorted, node.path) ?: node }
        }

        val flatTracks = sorted.flatMap { getAllTracksRecursive(it) }
        val currentTrack = _currentTrack.value
        if (currentTrack != null) {
            val parentPath = File(currentTrack.path).parentFile?.absolutePath
            val parentFolder = if (parentPath != null) findFolderInListRecursive(sorted, parentPath) else null
            if (parentFolder != null) {
                _playbackQueue.value = parentFolder.tracks
            } else {
                _playbackQueue.value = flatTracks
            }
        } else {
            _playbackQueue.value = flatTracks
        }
    }

    private fun sortFoldersAndTracks(folders: List<FolderNode>, sortType: SortType, sortOrder: SortOrder): List<FolderNode> {
        return folders.map { node ->
            val sortedSubs = sortFoldersAndTracks(node.subFolders, sortType, sortOrder)
            val sortedTracks = node.tracks.sortedWith { t1, t2 ->
                val comparison = when (sortType) {
                    SortType.TIME -> {
                        val m1 = File(t1.path).lastModified()
                        val m2 = File(t2.path).lastModified()
                        m1.compareTo(m2)
                    }
                    SortType.TYPE -> {
                        t1.format.lowercase().compareTo(t2.format.lowercase())
                    }
                    SortType.ALPHABET -> {
                        t1.title.lowercase().compareTo(t2.title.lowercase())
                    }
                }
                if (sortOrder == SortOrder.ASCENDING) comparison else -comparison
            }
            node.copy(
                subFolders = sortedSubs.toMutableList(),
                tracks = sortedTracks.toMutableList()
            )
        }.sortedBy { it.name.lowercase() }
    }

    private fun findFolderInListRecursive(list: List<FolderNode>, path: String): FolderNode? {
        for (node in list) {
            if (node.path == path) return node
            findFolderInListRecursive(node.subFolders, path)?.let { return it }
        }
        return null
    }

    fun openFolder(folder: FolderNode) {
        _folderNavigationStack.update { it + folder }
        _currentFolder.value = folder
    }

    fun navigateBackFolder() {
        _folderNavigationStack.update { stack ->
            if (stack.isNotEmpty()) {
                val newStack = stack.dropLast(1)
                _currentFolder.value = newStack.lastOrNull()
                newStack
            } else {
                _currentFolder.value = null
                stack
            }
        }
    }

    fun resetFolderNavigation() {
        _currentFolder.value = null
        _folderNavigationStack.value = emptyList()
    }

    private fun getAllTracksRecursive(node: FolderNode): List<AudioTrack> {
        val list = mutableListOf<AudioTrack>()
        list.addAll(node.tracks)
        for (sub in node.subFolders) {
            list.addAll(getAllTracksRecursive(sub))
        }
        return list
    }

    fun toggleFolderExpansion(path: String) {
        _expandedFolderPaths.update { current ->
            if (current.contains(path)) current - path else current + path
        }
    }

    fun selectTrack(track: AudioTrack) {
        // SMART PLAY/PAUSE TOGGLE ON CURRENTLY PLAYING TRACK:
        // IF the tapped song is CURRENTLY PLAYING or PAUSED (same track ID/path/URI):
        // Do NOT restart or replay the song from 0:00. Instead, toggle the playback state (Play -> Pause OR Pause -> Resume).
        if (_currentTrack.value?.path == track.path) {
            togglePlayPause()
            return
        }

        // IF a DIFFERENT track is tapped: Load the new track, reset playback position to 0:00, and start playing immediately.
        _currentTrack.value = track
        setLastPlayedTrackPath(track.path)
        
        // Ensure this track's parent folder tracks form the playback queue
        val parentPath = File(track.path).parentFile?.absolutePath
        val parentFolder = if (parentPath != null) findFolderInListRecursive(_folders.value, parentPath) else null
        if (parentFolder != null) {
            _playbackQueue.value = parentFolder.tracks
        } else {
            val currentQueue = _playbackQueue.value
            if (currentQueue.none { it.path == track.path }) {
                _playbackQueue.value = listOf(track)
            }
        }

        playTrackDirectly(track)
    }

    fun playTrackFromQueue(track: AudioTrack) {
        _currentTrack.value = track
        setLastPlayedTrackPath(track.path)
        playTrackDirectly(track)
    }

    private fun createMediaItem(track: AudioTrack): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setDisplayTitle(track.title)

        try {
            if (!track.customArtPath.isNullOrBlank() && File(track.customArtPath).exists()) {
                metadataBuilder.setArtworkUri(Uri.fromFile(File(track.customArtPath)))
            } else {
                val file = File(track.path)
                if (file.exists()) {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(track.path)
                    val artBytes = retriever.embeddedPicture
                    try { retriever.release() } catch (t: Throwable) {}
                    if (artBytes != null) {
                        metadataBuilder.setArtworkData(artBytes, null)
                    } else {
                        val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                        if (externalArtPath != null) {
                            metadataBuilder.setArtworkUri(Uri.fromFile(File(externalArtPath)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error attaching artwork to metadata: ${e.message}")
        }

        return MediaItem.Builder()
            .setUri(Uri.fromFile(File(track.path)))
            .setMediaId(track.path)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun playTrackDirectly(track: AudioTrack) {
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
                val mediaItem = createMediaItem(track)
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
                _isPlaying.value = true
                _currentPosition.value = 0L
                _duration.value = track.duration
                viewModelScope.launch {
                    playlistRepository.recordTrackPlayed(track.path)
                }
                loadLyricsForCurrentTrack()
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Failed to play track: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                if (player.mediaItemCount == 0 && _currentTrack.value != null) {
                    _currentTrack.value?.let { playTrackDirectly(it) }
                } else {
                    player.play()
                }
            }
        }
    }

    fun playNextTrack(): Boolean {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) {
            _queueBoundaryEvent.tryEmit("Reached at the bottom")
            return false
        }
        val current = _currentTrack.value
        if (current == null) {
            _queueBoundaryEvent.tryEmit("Reached at the bottom")
            return false
        }

        if (_repeatMode.value == RepeatMode.SHUFFLE && queue.size > 1) {
            val remaining = queue.filter { it.path != current.path }
            val nextTrack = remaining.random()
            playTrackFromQueue(nextTrack)
            return true
        }

        if (_repeatMode.value == RepeatMode.ONE) {
            playTrackFromQueue(current)
            return true
        }

        val currentIndex = queue.indexOfFirst { it.path == current.path }
        if (currentIndex != -1 && currentIndex < queue.size - 1) {
            playTrackFromQueue(queue[currentIndex + 1])
            return true
        } else if (_repeatMode.value == RepeatMode.ALL && queue.size > 1) {
            playTrackFromQueue(queue[0])
            return true
        } else {
            // Normal (OFF) mode: Reached the bottom of playback queue -> pause and notify boundary
            exoPlayer?.pause()
            _isPlaying.value = false
            _queueBoundaryEvent.tryEmit("Reached at the bottom")
            return false
        }
    }

    fun playPreviousTrack(): Boolean {
        val queue = _playbackQueue.value
        if (queue.isEmpty()) {
            _queueBoundaryEvent.tryEmit("Reached at the top")
            return false
        }
        val current = _currentTrack.value
        if (current == null) {
            _queueBoundaryEvent.tryEmit("Reached at the top")
            return false
        }

        if (_repeatMode.value == RepeatMode.SHUFFLE && queue.size > 1) {
            val remaining = queue.filter { it.path != current.path }
            val prevTrack = remaining.random()
            playTrackFromQueue(prevTrack)
            return true
        }

        if (_repeatMode.value == RepeatMode.ONE) {
            playTrackFromQueue(current)
            return true
        }

        val currentIndex = queue.indexOfFirst { it.path == current.path }
        if (currentIndex > 0) {
            playTrackFromQueue(queue[currentIndex - 1])
            return true
        } else if (_repeatMode.value == RepeatMode.ALL && queue.size > 1) {
            playTrackFromQueue(queue[queue.size - 1])
            return true
        } else {
            // Reached the top of playback queue
            _queueBoundaryEvent.tryEmit("Reached at the top")
            return false
        }
    }

    fun seekForward(offsetMs: Long = 10000L) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition + offsetMs).coerceAtMost(player.duration.coerceAtLeast(0L))
            player.seekTo(newPos)
            _currentPosition.value = newPos
        }
        triggerNotificationUpdate()
    }

    fun seekBackward(offsetMs: Long = 10000L) {
        exoPlayer?.let { player ->
            val newPos = (player.currentPosition - offsetMs).coerceAtLeast(0L)
            player.seekTo(newPos)
            _currentPosition.value = newPos
        }
        triggerNotificationUpdate()
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.let { player ->
            player.seekTo(positionMs)
            _currentPosition.value = positionMs
        }
        triggerNotificationUpdate()
    }

    private fun findTrackByPath(path: String): AudioTrack? {
        // Try to find inside current queue first
        _playbackQueue.value.find { it.path == path }?.let { return it }

        // Or search all folders recursively
        for (root in _folders.value) {
            findTrackRecursive(root, path)?.let { return it }
        }
        return null
    }

    private fun findTrackRecursive(node: FolderNode, path: String): AudioTrack? {
        node.tracks.find { it.path == path }?.let { return it }
        for (sub in node.subFolders) {
            findTrackRecursive(sub, path)?.let { return it }
        }
        return null
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressJob = viewModelScope.launch {
            var lastNotifySec = -1L
            while (true) {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        _currentPosition.value = pos

                        // A/B loop check
                        val start = _loopStartMs.value
                        val end = _loopEndMs.value
                        if (start != null && end != null && pos >= end) {
                            player.seekTo(start)
                            _currentPosition.value = start
                        }

                        val currentSec = pos / 1000L
                        if (currentSec != lastNotifySec) {
                            lastNotifySec = currentSec
                            triggerNotificationUpdate()
                        }
                    }
                }
                delay(100) // 100ms real-time precision polling for lyrics and seekbar
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    fun setShowHiddenFiles(show: Boolean) {
        _showHiddenFiles.value = show
        prefs.edit().putBoolean("show_hidden_files", show).apply()
        scanAudio(force = true)
    }

    fun setScrollToLastPlayed(scroll: Boolean) {
        _scrollToLastPlayed.value = scroll
        prefs.edit().putBoolean("scroll_to_last_played", scroll).apply()
    }

    fun setLastPlayedTrackPath(path: String) {
        _lastPlayedTrackPath.value = path
        prefs.edit().putString("last_played_track", path).apply()
    }

    fun setEqEnabled(enabled: Boolean) {
        _eqEnabled.value = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        applyEqualizerSettings()
    }

    fun setEqBand(index: Int, value: Float) {
        val current = _eqBands.value.toMutableList()
        if (index in current.indices) {
            val clamped = value.coerceIn(-15f, 15f)
            current[index] = clamped
            _eqBands.value = current
            prefs.edit().putFloat("eq_band_$index", clamped).apply()
            applyEqualizerSettings()
        }
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    fun setEightDEnabled(enabled: Boolean) {
        _eightDEnabled.value = enabled
        prefs.edit().putBoolean("eight_d_enabled", enabled).apply()
        eightDAudioProcessor?.enabled = enabled
        applyVirtualizerSettings()
    }

    fun setEightDPreset(name: String) {
        val key = name.lowercase()
        val preset = EIGHT_D_PRESETS[key] ?: return
        _eightDPreset.value = key
        prefs.edit().putString("eight_d_preset", key).apply()
        setEightDSpeed(preset.first, isFromPreset = true)
        setEightDDepth(preset.second, isFromPreset = true)
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    fun setEightDSpeed(speed: Float, isFromPreset: Boolean = false) {
        val s = speed.coerceIn(0.01f, 1.0f)
        _eightDSpeed.value = s
        if (!isFromPreset) {
            _eightDPreset.value = "custom"
            prefs.edit().putString("eight_d_preset", "custom").apply()
        }
        prefs.edit().putFloat("eight_d_speed", s).apply()
        eightDAudioProcessor?.speed = s
    }

    @OptIn(androidx.media3.common.util.UnstableApi::class)
    fun setEightDDepth(depth: Float, isFromPreset: Boolean = false) {
        val d = depth.coerceIn(0f, 100f)
        _eightDDepth.value = d
        if (!isFromPreset) {
            _eightDPreset.value = "custom"
            prefs.edit().putString("eight_d_preset", "custom").apply()
        }
        prefs.edit().putFloat("eight_d_depth", d).apply()
        eightDAudioProcessor?.depth = d
        applyVirtualizerSettings()
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun playLastPlayedTrack() {
        val lastPath = _lastPlayedTrackPath.value
        if (lastPath.isNotEmpty()) {
            val track = findTrackByPath(lastPath)
            if (track != null) {
                if (_currentTrack.value?.path == track.path) {
                    togglePlayPause()
                } else {
                    selectTrack(track)
                }
                return
            }
        }
        val firstTrack = getAllTracks().firstOrNull()
        if (firstTrack != null) {
            selectTrack(firstTrack)
        }
    }

    fun getAllTracks(): List<AudioTrack> {
        val result = mutableListOf<AudioTrack>()
        fun collect(node: FolderNode) {
            result.addAll(node.tracks)
            for (sub in node.subFolders) {
                collect(sub)
            }
        }
        for (root in _folders.value) {
            collect(root)
        }
        return result
    }

    fun applyEqualizerSettings() {
        val sessionId = exoPlayer?.audioSessionId ?: return
        if (sessionId == 0) return
        
        try {
            if (androidEqualizer == null || androidEqualizer?.id != sessionId) {
                androidEqualizer?.release()
                androidEqualizer = android.media.audiofx.Equalizer(0, sessionId).apply {
                    enabled = _eqEnabled.value
                }
            }
            
            val eq = androidEqualizer ?: return
            eq.enabled = _eqEnabled.value
            if (_eqEnabled.value) {
                val bandsCount = eq.numberOfBands
                val bandsValues = _eqBands.value
                for (i in 0 until bandsCount.toInt()) {
                    if (i < bandsValues.size) {
                        val milliBels = (bandsValues[i] * 100).toInt().toShort()
                        val range = eq.bandLevelRange
                        val minLevel = range?.get(0) ?: -1500
                        val maxLevel = range?.get(1) ?: 1500
                        val clamped = milliBels.coerceIn(minLevel, maxLevel)
                        eq.setBandLevel(i.toShort(), clamped)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error applying Equalizer settings: ${e.message}")
        }
    }

    fun applyVirtualizerSettings() {
        val sessionId = exoPlayer?.audioSessionId ?: return
        if (sessionId <= 0) return
        
        try {
            if (androidVirtualizer == null || androidVirtualizer?.id != sessionId) {
                try { androidVirtualizer?.release() } catch (e: Exception) {}
                androidVirtualizer = android.media.audiofx.Virtualizer(0, sessionId)
            }
            androidVirtualizer?.enabled = _eightDEnabled.value
            if (_eightDEnabled.value) {
                val maxStrength = (_eightDDepth.value * 10).toInt().toShort()
                androidVirtualizer?.setStrength(maxStrength)
            }
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Error applying Virtualizer: ${e.message}")
        }
    }

    private fun updateSpatialEffect() {
        applyVirtualizerSettings()
    }

    // Multi-select / File Manager
    fun toggleSelection(path: String) {
        _selectedPaths.update { current ->
            if (current.contains(path)) current - path else current + path
        }
    }

    fun enterSelectionMode(initialPath: String? = null) {
        _isSelectionMode.value = true
        if (initialPath != null) {
            _selectedPaths.value = setOf(initialPath)
        } else {
            _selectedPaths.value = emptySet()
        }
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedPaths.value = emptySet()
    }

    fun performDeleteSelected(context: Context) {
        val paths = _selectedPaths.value.toList()
        if (paths.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentPlayingPath = _currentTrack.value?.path
                var wasCurrentTrackDeleted = false
                val deletedPaths = mutableListOf<String>()

                for (path in paths) {
                    val file = File(path)
                    if (file.exists()) {
                        deleteRecursive(file)
                        deletedPaths.add(path)
                    }

                    if (path == currentPlayingPath) {
                        wasCurrentTrackDeleted = true
                    }

                    // Remove from Room playlist & stats DB
                    try {
                        playlistRepository.deleteTrackFromAllPlaylists(path)
                        playlistRepository.deleteTrackStats(path)
                    } catch (e: Exception) {
                        Log.e("PlaybackVM", "Error removing track from Room DB: ${e.message}")
                    }

                    // Delete from MediaStore via ContentResolver
                    try {
                        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        val where = "${MediaStore.Audio.Media.DATA} = ?"
                        val selectionArgs = arrayOf(path)
                        context.contentResolver.delete(uri, where, selectionArgs)
                    } catch (e: Exception) {
                        Log.w("PlaybackVM", "Could not delete from MediaStore: ${e.message}")
                    }
                }

                if (wasCurrentTrackDeleted) {
                    withContext(Dispatchers.Main) {
                        exoPlayer?.stop()
                        exoPlayer?.clearMediaItems()
                        _currentTrack.value = null
                        _isPlaying.value = false
                    }
                }

                // Immediately trigger MediaScanner rescan for all deleted files
                if (deletedPaths.isNotEmpty()) {
                    MediaScannerConnection.scanFile(
                        getApplication(),
                        deletedPaths.toTypedArray(),
                        null
                    ) { p, uri ->
                        Log.d("PlaybackVM", "Scanned deleted file: $p -> $uri")
                    }
                }

                scanAudio(force = true)
                withContext(Dispatchers.Main) {
                    exitSelectionMode()
                }
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Error deleting files: ${e.message}")
            }
        }
    }

    private fun deleteRecursive(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursive(it) }
        }
        file.delete()
    }

    fun performRenameSelected(newName: String) {
        val paths = _selectedPaths.value
        // Strict single-file rename constraint: only works when 1 file is selected
        if (paths.size != 1) return
        val path = paths.first()
        val currentT = findTrackByPath(path)
        if (currentT != null) {
            renameTrackFile(currentT, newName)
            exitSelectionMode()
            scanAudio(force = true)
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val file = File(path)
                    val parentDir = file.parentFile
                    if (parentDir != null && file.exists()) {
                        val ext = file.extension
                        val cleanName = if (file.isDirectory || ext.isEmpty()) newName else {
                            if (newName.endsWith(".$ext", ignoreCase = true)) newName else "$newName.$ext"
                        }
                        val destination = File(parentDir, cleanName)
                        if (file.renameTo(destination)) {
                            playlistRepository.updateTrackPath(path, destination.absolutePath)
                            MediaScannerConnection.scanFile(
                                getApplication(),
                                arrayOf(path, destination.absolutePath),
                                null
                            ) { scannedPath, uri ->
                                Log.d("PlaybackVM", "MediaScanner scan on rename: $scannedPath -> $uri")
                            }
                            scanAudio(force = true)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        exitSelectionMode()
                    }
                } catch (e: Exception) {
                    Log.e("PlaybackVM", "Error renaming file: ${e.message}")
                }
            }
        }
    }

    // --- PLAYLIST MANAGEMENT OPERATIONS ---

    fun createPlaylist(
        title: String,
        description: String = "",
        customArtPath: String? = null,
        initialTrackPath: String? = null,
        onResult: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val newId = playlistRepository.createPlaylist(title, description, customArtPath)
            if (initialTrackPath != null) {
                playlistRepository.addTrackToPlaylist(newId, initialTrackPath)
            }
            onResult(newId)
        }
    }

    fun addTrackToPlaylist(playlistId: Long, trackPath: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = playlistRepository.addTrackToPlaylist(playlistId, trackPath)
            onResult(success)
        }
    }

    fun addTracksToPlaylist(playlistId: Long, trackPaths: List<String>, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val count = playlistRepository.addTracksToPlaylist(playlistId, trackPaths)
            onResult(count)
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackPath: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackPath)
        }
    }

    fun removeTracksFromPlaylist(playlistId: Long, trackPaths: List<String>) {
        viewModelScope.launch {
            playlistRepository.removeTracksFromPlaylist(playlistId, trackPaths)
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            playlistRepository.moveTrack(playlistId, fromIndex, toIndex)
        }
    }

    fun reorderPlaylistTracks(playlistId: Long, orderedPaths: List<String>) {
        viewModelScope.launch {
            playlistRepository.reorderTracks(playlistId, orderedPaths)
        }
    }

    fun updatePlaylistInfo(playlist: PlaylistEntity) {
        viewModelScope.launch {
            playlistRepository.updatePlaylist(playlist)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun toggleFavorite(trackPath: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val isFav = playlistRepository.toggleFavorite(trackPath)
            onResult(isFav)
            com.example.widget.PlayerWidgetProvider.updateAllWidgets(getApplication())
            triggerNotificationUpdate()
        }
    }

    fun isFavorite(trackPath: String): Boolean {
        return favoriteStats.value.any { it.trackPath == trackPath && it.isFavorite }
    }

    fun getPlaylistTracksFlow(playlistId: Long) = playlistRepository.getTracksForPlaylist(playlistId)

    fun playPlaylist(tracks: List<AudioTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val targetTrack = tracks.getOrNull(startIndex) ?: tracks[0]
        // If tapping the track that is already playing/paused, toggle playback without resetting position
        if (!shuffle && _currentTrack.value?.path == targetTrack.path) {
            togglePlayPause()
            return
        }
        val queue = if (shuffle) tracks.shuffled() else tracks
        _playbackQueue.value = queue
        val startTrack = if (shuffle) queue[0] else targetTrack
        _currentTrack.value = startTrack
        setLastPlayedTrackPath(startTrack.path)
        playTrackDirectly(startTrack)
    }

    fun getSmartPlaylistTracks(type: SmartPlaylistType): List<AudioTrack> {
        val allTracks = getAllTracks()
        return when (type) {
            SmartPlaylistType.RECENTLY_ADDED -> {
                allTracks.sortedByDescending { File(it.path).lastModified() }.take(50)
            }
            SmartPlaylistType.MOST_PLAYED -> {
                val statsMap = mostPlayedStats.value.associateBy { it.trackPath }
                allTracks.filter { statsMap.containsKey(it.path) }
                    .sortedByDescending { statsMap[it.path]?.playCount ?: 0 }
            }
            SmartPlaylistType.FAVORITES -> {
                val favPaths = favoriteStats.value.filter { it.isFavorite }.map { it.trackPath }.toSet()
                allTracks.filter { favPaths.contains(it.path) }
            }
        }
    }

    fun resolveTracks(trackEntities: List<PlaylistTrackEntity>): List<AudioTrack> {
        val allTracks = getAllTracks()
        val trackMap = allTracks.associateBy { it.path }
        return trackEntities.mapNotNull { trackMap[it.trackPath] }
    }

    suspend fun exportSettingsBackup(uri: Uri, contentResolver: ContentResolver): Result<String> = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject()
            root.put("app", "Ongaku7")
            root.put("version", 1)
            root.put("exportTimestamp", System.currentTimeMillis())

            // 1. UI Customization Preferences
            val prefObj = JSONObject()
            prefObj.put("icon_type", _iconType.value.name)
            prefObj.put("album_art_type", _albumArtType.value.name)
            prefObj.put("show_hidden_files", _showHiddenFiles.value)
            prefObj.put("scroll_to_last_played", _scrollToLastPlayed.value)
            prefObj.put("sort_type", _sortType.value.name)
            prefObj.put("sort_order", _sortOrder.value.name)
            val removedArtArray = JSONArray()
            _removedArtTrackPaths.value.forEach { removedArtArray.put(it) }
            prefObj.put("removed_art_paths", removedArtArray)
            root.put("preferences", prefObj)

            // 2. Audio Configuration, Equalizer & 8D Audio
            val audioObj = JSONObject()
            audioObj.put("tempo", _tempo.value.toDouble())
            audioObj.put("pitch", _pitch.value.toDouble())
            audioObj.put("reverb_level", _reverbLevel.value.toDouble())
            audioObj.put("reverb_system", _reverbSystem.value.name)
            audioObj.put("reverb_hz", _reverbHz.value.toDouble())
            audioObj.put("bass_boost_strength", _bassBoostStrength.value.toDouble())
            audioObj.put("repeat_mode", _repeatMode.value.name)

            // Equalizer
            audioObj.put("eq_enabled", _eqEnabled.value)
            val eqArray = JSONArray()
            _eqBands.value.forEach { eqArray.put(it.toDouble()) }
            audioObj.put("eq_bands", eqArray)

            // 8D Audio
            audioObj.put("eight_d_enabled", _eightDEnabled.value)
            audioObj.put("eight_d_speed", _eightDSpeed.value.toDouble())
            audioObj.put("eight_d_depth", _eightDDepth.value.toDouble())
            audioObj.put("eight_d_preset", _eightDPreset.value)
            root.put("audioConfig", audioObj)

            // 3. Custom Playlists & Tracks
            val playlistsList = playlistRepository.allPlaylists.first()
            val playlistsArray = JSONArray()
            for (p in playlistsList) {
                val pObj = JSONObject()
                pObj.put("title", p.title)
                pObj.put("description", p.description)
                pObj.put("createdAt", p.createdAt)
                if (p.customArtPath != null) {
                    pObj.put("customArtPath", p.customArtPath)
                }
                val tracksArray = JSONArray()
                val tracks = playlistRepository.getTracksForPlaylist(p.id).first()
                for (t in tracks) {
                    val tObj = JSONObject()
                    tObj.put("trackPath", t.trackPath)
                    tObj.put("orderIndex", t.orderIndex)
                    tObj.put("addedAt", t.addedAt)
                    tracksArray.put(tObj)
                }
                pObj.put("tracks", tracksArray)
                playlistsArray.put(pObj)
            }
            root.put("playlists", playlistsArray)

            contentResolver.openOutputStream(uri)?.use { os ->
                os.write(root.toString(2).toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: return@withContext Result.failure(Exception("Unable to open output stream for selected file."))

            Result.success("Exported ${playlistsList.size} playlists & all audio settings successfully!")
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Export error", e)
            Result.failure(e)
        }
    }

    suspend fun importSettingsBackup(uri: Uri, contentResolver: ContentResolver): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } ?: return@withContext Result.failure(Exception("Unable to open file for reading."))

            val root = JSONObject(jsonString)
            if (!root.has("app") || root.optString("app") != "Ongaku7") {
                return@withContext Result.failure(IllegalArgumentException("Invalid backup file: Signature is not Ongaku7."))
            }

            // 1. Preferences hot-reload
            if (root.has("preferences")) {
                val prefObj = root.getJSONObject("preferences")
                if (prefObj.has("icon_type")) {
                    val name = prefObj.getString("icon_type")
                    val type = try { IconType.valueOf(name) } catch (e: Exception) { IconType.CYBERPUNK }
                    withContext(Dispatchers.Main) { setIconType(type) }
                }
                if (prefObj.has("album_art_type")) {
                    val name = prefObj.getString("album_art_type")
                    val type = try { AlbumArtType.valueOf(name) } catch (e: Exception) { AlbumArtType.ROUNDED }
                    withContext(Dispatchers.Main) { setAlbumArtType(type) }
                }
                if (prefObj.has("show_hidden_files")) {
                    val show = prefObj.getBoolean("show_hidden_files")
                    withContext(Dispatchers.Main) { setShowHiddenFiles(show) }
                }
                if (prefObj.has("scroll_to_last_played")) {
                    val scroll = prefObj.getBoolean("scroll_to_last_played")
                    withContext(Dispatchers.Main) { setScrollToLastPlayed(scroll) }
                }
                if (prefObj.has("sort_type")) {
                    val name = prefObj.getString("sort_type")
                    val type = try { SortType.valueOf(name) } catch (e: Exception) { SortType.TIME }
                    withContext(Dispatchers.Main) { setSortType(type) }
                }
                if (prefObj.has("sort_order")) {
                    val name = prefObj.getString("sort_order")
                    val order = try { SortOrder.valueOf(name) } catch (e: Exception) { SortOrder.DESCENDING }
                    withContext(Dispatchers.Main) { setSortOrder(order) }
                }
                if (prefObj.has("removed_art_paths")) {
                    val arr = prefObj.getJSONArray("removed_art_paths")
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    _removedArtTrackPaths.value = set
                    prefs.edit().putStringSet("removed_art_paths", set).apply()
                }
            }

            // 2. Audio configuration & effects hot-reload
            if (root.has("audioConfig")) {
                val audioObj = root.getJSONObject("audioConfig")
                if (audioObj.has("tempo")) {
                    val tempoVal = audioObj.getDouble("tempo").toFloat()
                    withContext(Dispatchers.Main) { setTempo(tempoVal) }
                }
                if (audioObj.has("pitch")) {
                    val pitchVal = audioObj.getDouble("pitch").toFloat()
                    withContext(Dispatchers.Main) { setPitch(pitchVal) }
                }
                if (audioObj.has("reverb_level")) {
                    val level = audioObj.getDouble("reverb_level").toFloat()
                    withContext(Dispatchers.Main) { setReverbLevel(level) }
                }
                if (audioObj.has("reverb_system")) {
                    val name = audioObj.getString("reverb_system")
                    val sys = try { ReverbSystem.valueOf(name) } catch (e: Exception) { ReverbSystem.ROOM }
                    withContext(Dispatchers.Main) { setReverbSystem(sys) }
                }
                if (audioObj.has("reverb_hz")) {
                    val hz = audioObj.getDouble("reverb_hz").toFloat()
                    withContext(Dispatchers.Main) { setReverbHz(hz) }
                }
                if (audioObj.has("bass_boost_strength")) {
                    val bass = audioObj.getDouble("bass_boost_strength").toFloat()
                    withContext(Dispatchers.Main) { setBassBoostStrength(bass) }
                }
                if (audioObj.has("repeat_mode")) {
                    val name = audioObj.getString("repeat_mode")
                    val rep = try { RepeatMode.valueOf(name) } catch (e: Exception) { RepeatMode.OFF }
                    withContext(Dispatchers.Main) { setRepeatMode(rep) }
                }

                // Equalizer
                val eqEn = audioObj.optBoolean("eq_enabled", false)
                val eqArr = audioObj.optJSONArray("eq_bands")
                val bands: List<Float> = if (eqArr != null) {
                    List(eqArr.length()) { i -> eqArr.getDouble(i).toFloat() }
                } else listOf(0f, 0f, 0f, 0f, 0f)
                withContext(Dispatchers.Main) {
                    setEqEnabled(eqEn)
                    bands.forEachIndexed { index: Int, level: Float ->
                        setEqBand(index, level)
                    }
                }

                // 8D Audio
                val eightDEn = audioObj.optBoolean("eight_d_enabled", false)
                val eightDSpeedVal = audioObj.optDouble("eight_d_speed", 0.15).toFloat()
                val eightDDepthVal = audioObj.optDouble("eight_d_depth", 90.0).toFloat()
                val eightDPresetVal = audioObj.optString("eight_d_preset", "classic")
                withContext(Dispatchers.Main) {
                    setEightDEnabled(eightDEn)
                    setEightDSpeed(eightDSpeedVal)
                    setEightDDepth(eightDDepthVal)
                    setEightDPreset(eightDPresetVal)
                }
            }

            // 3. Playlists restore
            var restoredPlaylists = 0
            if (root.has("playlists")) {
                val pArr = root.getJSONArray("playlists")
                val existingPlaylists = playlistRepository.allPlaylists.first()
                for (i in 0 until pArr.length()) {
                    val pObj = pArr.getJSONObject(i)
                    val title = pObj.getString("title")
                    val description = pObj.optString("description", "")
                    val customArt = if (pObj.isNull("customArtPath")) null else pObj.optString("customArtPath", null)

                    val existing = existingPlaylists.find { it.title.equals(title, ignoreCase = true) }
                    val playlistId = existing?.id ?: playlistRepository.createPlaylist(title, description, customArt)

                    if (pObj.has("tracks")) {
                        val tArr = pObj.getJSONArray("tracks")
                        for (j in 0 until tArr.length()) {
                            val tObj = tArr.getJSONObject(j)
                            val path = tObj.getString("trackPath")
                            playlistRepository.addTrackToPlaylist(playlistId, path)
                        }
                    }
                    restoredPlaylists++
                }
            }

            withContext(Dispatchers.Main) {
                applyEqualizerSettings()
                applyReverb()
                applyBassBoost()
                updateSpatialEffect()
            }

            Result.success("Restored audio configuration and $restoredPlaylists playlists successfully!")
        } catch (e: Exception) {
            Log.e("PlaybackVM", "Import error", e)
            Result.failure(e)
        }
    }

    fun shutdownPlayer() {
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
            } catch (e: Exception) {
                Log.e("PlaybackVM", "Error stopping player: ${e.message}")
            }
        }
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (e: Exception) {}
        try {
            presetReverb?.release()
            presetReverb = null
        } catch (e: Exception) {}
        try {
            androidEqualizer?.release()
            androidEqualizer = null
        } catch (e: Exception) {}
        try {
            androidVirtualizer?.release()
            androidVirtualizer = null
        } catch (e: Exception) {}

        _isPlaying.value = false
        _currentTrack.value = null
        _currentPosition.value = 0L
        _duration.value = 0L
        stopProgressPolling()

        MediaPlaybackService.stopServiceAndClearNotification(getApplication())
        com.example.widget.PlayerWidgetProvider.updateAllWidgets(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(noisyAndBluetoothReceiver)
        } catch (e: Exception) {}
        stopProgressPolling()
        panningJob?.cancel()
        panningJob = null
        try {
            bassBoost?.release()
            bassBoost = null
        } catch (e: Exception) {}
        try {
            presetReverb?.release()
            presetReverb = null
        } catch (e: Exception) {}
        try {
            androidEqualizer?.release()
            androidEqualizer = null
        } catch (e: Exception) {}
        try {
            androidVirtualizer?.release()
            androidVirtualizer = null
        } catch (e: Exception) {}
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        MediaPlaybackService.stopService(getApplication())
        instance = null
    }

    companion object {
        var instance: PlaybackViewModel? = null
            private set

        val EIGHT_D_PRESETS = mapOf(
            "classic" to Pair(0.15f, 90f),
            "gentle" to Pair(0.07f, 70f),
            "intense" to Pair(0.40f, 100f),
            "dreamy" to Pair(0.04f, 85f)
        )
    }
}
