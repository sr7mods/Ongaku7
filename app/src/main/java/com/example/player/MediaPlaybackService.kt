package com.example.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.palette.graphics.Palette
import com.example.MainActivity
import com.example.R
import com.example.data.AudioTrack
import java.io.File

class MediaPlaybackService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val track = PlaybackViewModel.instance?.currentTrack?.value
        val isPlaying = PlaybackViewModel.instance?.isPlaying?.value ?: false
        val progress = PlaybackViewModel.instance?.currentPosition?.value ?: 0L
        val duration = PlaybackViewModel.instance?.duration?.value ?: 0L
        val repeatMode = PlaybackViewModel.instance?.repeatMode?.value ?: RepeatMode.OFF

        val notification = createNotification(this, track, isPlaying, progress, duration, repeatMode)
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error starting foreground service: ${e.message}")
        }

        intent?.action?.let { action ->
            when (action) {
                ACTION_PLAY_PAUSE -> PlaybackViewModel.instance?.togglePlayPause()
                ACTION_PREVIOUS -> PlaybackViewModel.instance?.playPreviousTrack()
                ACTION_NEXT -> PlaybackViewModel.instance?.playNextTrack()
                ACTION_TOGGLE_LOOP -> PlaybackViewModel.instance?.toggleRepeatMode()
                ACTION_SEEK_FORWARD -> PlaybackViewModel.instance?.seekForward()
                ACTION_SEEK_BACKWARD -> PlaybackViewModel.instance?.seekBackward()
                ACTION_TOGGLE_FAVORITE -> {
                    val current = PlaybackViewModel.instance?.currentTrack?.value
                    if (current != null) {
                        PlaybackViewModel.instance?.toggleFavorite(current.path)
                    }
                }
                ACTION_STOP -> stopForegroundAndService()
            }
        }
        return START_NOT_STICKY
    }

    private fun stopForegroundAndService() {
        try {
            PlaybackViewModel.instance?.shutdownPlayer()
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error shutting down player: ${e.message}")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error stopping foreground: ${e.message}")
        }

        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e("MediaPlaybackService", "Error canceling notification: ${e.message}")
        }

        stopSelf()
        isServiceRunning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
    }

    companion object {
        const val CHANNEL_ID = "ongaku7_playback_channel"
        const val NOTIFICATION_ID = 7007

        const val ACTION_PLAY_PAUSE = "com.example.ACTION_PLAY_PAUSE"
        const val ACTION_PREVIOUS = "com.example.ACTION_PREVIOUS"
        const val ACTION_NEXT = "com.example.ACTION_NEXT"
        const val ACTION_TOGGLE_LOOP = "com.example.ACTION_TOGGLE_LOOP"
        const val ACTION_SEEK_FORWARD = "com.example.ACTION_SEEK_FORWARD"
        const val ACTION_SEEK_BACKWARD = "com.example.ACTION_SEEK_BACKWARD"
        const val ACTION_TOGGLE_FAVORITE = "com.example.ACTION_TOGGLE_FAVORITE"
        const val ACTION_STOP = "com.example.ACTION_STOP"

        var isServiceRunning = false
            private set

        private var lastUpdateTime = 0L
        private var lastIsPlaying = false
        private var lastTrackPath = ""

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Ongaku7 Playback Control",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Media controls for premium playback"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.createNotificationChannel(channel)
            }
        }

        fun startService(context: Context) {
            if (!isServiceRunning) {
                val intent = Intent(context, MediaPlaybackService::class.java)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    isServiceRunning = true
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Failed to start service: ${e.message}")
                }
            }
        }

        fun stopService(context: Context) {
            if (isServiceRunning) {
                val intent = Intent(context, MediaPlaybackService::class.java)
                context.stopService(intent)
                isServiceRunning = false
            }
        }

        fun stopServiceAndClearNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.e("MediaPlaybackService", "Error canceling notification: ${e.message}")
            }
            if (isServiceRunning) {
                val intent = Intent(context, MediaPlaybackService::class.java).apply {
                    action = ACTION_STOP
                }
                context.stopService(intent)
                isServiceRunning = false
            }
        }

        fun updateNotification(
            context: Context,
            track: AudioTrack?,
            isPlaying: Boolean,
            progressMs: Long,
            durationMs: Long,
            repeatMode: RepeatMode
        ) {
            if (track == null) return
            if (!isServiceRunning) {
                startService(context)
            }

            val currentTime = System.currentTimeMillis()
            val stateChanged = isPlaying != lastIsPlaying || track.path != lastTrackPath
            if (!stateChanged && (currentTime - lastUpdateTime < 400L)) {
                return
            }

            lastUpdateTime = currentTime
            lastIsPlaying = isPlaying
            lastTrackPath = track.path

            val notification = createNotification(context, track, isPlaying, progressMs, durationMs, repeatMode)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }

        fun createNotification(
            context: Context,
            track: AudioTrack?,
            isPlaying: Boolean,
            progressMs: Long,
            durationMs: Long,
            repeatMode: RepeatMode
        ): Notification {
            createNotificationChannel(context)

            // Intent to open app when clicking notification
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val clickPendingIntent = PendingIntent.getActivity(
                context, 0, clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Button Intents
            val playPauseIntent = Intent(context, MediaPlaybackService::class.java).apply { action = ACTION_PLAY_PAUSE }
            val playPausePending = PendingIntent.getService(context, 1, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val prevIntent = Intent(context, MediaPlaybackService::class.java).apply { action = ACTION_PREVIOUS }
            val prevPending = PendingIntent.getService(context, 2, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val nextIntent = Intent(context, MediaPlaybackService::class.java).apply { action = ACTION_NEXT }
            val nextPending = PendingIntent.getService(context, 3, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val loopIntent = Intent(context, MediaPlaybackService::class.java).apply { action = ACTION_TOGGLE_LOOP }
            val loopPending = PendingIntent.getService(context, 4, loopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val stopIntent = Intent(context, MediaPlaybackService::class.java).apply { action = ACTION_STOP }
            val stopPending = PendingIntent.getService(context, 5, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val seekFwdIntent = Intent(context, MediaPlaybackService::class.java).apply { action = ACTION_SEEK_FORWARD }
            val seekFwdPending = PendingIntent.getService(context, 6, seekFwdIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val trackTitle = track?.title ?: "No Track Playing"
            val trackArtist = track?.artist ?: "Unknown Artist"

            // 1. Small View (RemoteViews for pre-Android 13)
            val smallViews = RemoteViews(context.packageName, R.layout.notification_glassy).apply {
                setTextViewText(R.id.track_title, trackTitle)
                setTextViewText(R.id.track_artist, trackArtist)
                setImageViewResource(
                    R.id.btn_play_pause,
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
                // 4-state loop icon and color status indicator
                setImageViewResource(
                    R.id.btn_loop,
                    when (repeatMode) {
                        RepeatMode.OFF -> R.drawable.ic_repeat
                        RepeatMode.ALL -> R.drawable.ic_repeat_all
                        RepeatMode.ONE -> R.drawable.ic_repeat_one
                        RepeatMode.SHUFFLE -> R.drawable.ic_shuffle
                    }
                )
                setInt(
                    R.id.btn_loop,
                    "setColorFilter",
                    when (repeatMode) {
                        RepeatMode.OFF -> 0xFF888888.toInt()
                        RepeatMode.ALL -> 0xFF00F5FF.toInt()
                        RepeatMode.ONE -> 0xFF00F5FF.toInt()
                        RepeatMode.SHUFFLE -> 0xFF00F5FF.toInt()
                    }
                )

                setOnClickPendingIntent(R.id.btn_play_pause, playPausePending)
                setOnClickPendingIntent(R.id.btn_prev, prevPending)
                setOnClickPendingIntent(R.id.btn_next, nextPending)
                setOnClickPendingIntent(R.id.btn_loop, loopPending)
                setOnClickPendingIntent(R.id.btn_stop, stopPending)
            }

            // 2. Big/Expanded View (RemoteViews for pre-Android 13)
            val bigViews = RemoteViews(context.packageName, R.layout.notification_glassy_big).apply {
                setTextViewText(R.id.track_title, trackTitle)
                setTextViewText(R.id.track_artist, trackArtist)
                setImageViewResource(
                    R.id.btn_play_pause,
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
                )
                // 4-state loop icon and color status indicator
                setImageViewResource(
                    R.id.btn_loop,
                    when (repeatMode) {
                        RepeatMode.OFF -> R.drawable.ic_repeat
                        RepeatMode.ALL -> R.drawable.ic_repeat_all
                        RepeatMode.ONE -> R.drawable.ic_repeat_one
                        RepeatMode.SHUFFLE -> R.drawable.ic_shuffle
                    }
                )
                setInt(
                    R.id.btn_loop,
                    "setColorFilter",
                    when (repeatMode) {
                        RepeatMode.OFF -> 0xFF888888.toInt()
                        RepeatMode.ALL -> 0xFF00F5FF.toInt()
                        RepeatMode.ONE -> 0xFF00F5FF.toInt()
                        RepeatMode.SHUFFLE -> 0xFF00F5FF.toInt()
                    }
                )

                // Update Progress bar
                val progressPercent = if (durationMs > 0) ((progressMs * 100) / durationMs).toInt() else 0
                setProgressBar(R.id.progress_bar, 100, progressPercent, false)
                setOnClickPendingIntent(R.id.progress_bar, seekFwdPending)

                setOnClickPendingIntent(R.id.btn_play_pause, playPausePending)
                setOnClickPendingIntent(R.id.btn_prev, prevPending)
                setOnClickPendingIntent(R.id.btn_next, nextPending)
                setOnClickPendingIntent(R.id.btn_loop, loopPending)
                setOnClickPendingIntent(R.id.btn_stop, stopPending)
            }

            // Load album art thumbnail
            var artBitmap: Bitmap? = null
            if (track != null) {
                try {
                    if (!track.customArtPath.isNullOrBlank() && File(track.customArtPath).exists()) {
                        artBitmap = BitmapFactory.decodeFile(track.customArtPath)
                    } else {
                        val file = File(track.path)
                        if (file.exists()) {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(track.path)
                            val artBytes = retriever.embeddedPicture
                            try { retriever.release() } catch (t: Throwable) {}
                            if (artBytes != null) {
                                val options = BitmapFactory.Options().apply {
                                    inSampleSize = 2 // high fidelity for modern media controls
                                }
                                artBitmap = BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                            } else {
                                val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                                if (externalArtPath != null) {
                                    artBitmap = BitmapFactory.decodeFile(externalArtPath)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                    if (externalArtPath != null) {
                        artBitmap = BitmapFactory.decodeFile(externalArtPath)
                    }
                }
            }

            // Dynamic Palette color extraction
            var accentColor = 0xFF00F5FF.toInt()
            if (artBitmap != null) {
                smallViews.setImageViewBitmap(R.id.album_art, artBitmap)
                bigViews.setImageViewBitmap(R.id.album_art, artBitmap)
                
                smallViews.setImageViewBitmap(R.id.notification_bg_image, artBitmap)
                bigViews.setImageViewBitmap(R.id.notification_bg_image, artBitmap)

                try {
                    val palette = Palette.from(artBitmap).generate()
                    accentColor = palette.getVibrantColor(
                        palette.getDominantColor(0xFF00F5FF.toInt())
                    )
                } catch (e: Exception) {
                    Log.e("MediaPlaybackService", "Palette extraction: ${e.message}")
                }
            } else {
                smallViews.setImageViewResource(R.id.album_art, R.mipmap.ic_launcher)
                bigViews.setImageViewResource(R.id.album_art, R.mipmap.ic_launcher)
                
                smallViews.setImageViewResource(R.id.notification_bg_image, R.mipmap.ic_launcher)
                bigViews.setImageViewResource(R.id.notification_bg_image, R.mipmap.ic_launcher)
            }

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_play_arrow)
                .setContentTitle(trackTitle)
                .setContentText(trackArtist)
                .setSubText(track?.album)
                .setContentIntent(clickPendingIntent)
                .setOngoing(isPlaying)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setColor(accentColor)
                .setColorized(true)

            if (artBitmap != null) {
                builder.setLargeIcon(artBitmap)
            }

            // Standard Media Actions (for lock screen, watches, Bluetooth & Android 13+ System Media Controls)
            val prevAction = NotificationCompat.Action(
                R.drawable.ic_skip_previous,
                "Previous",
                prevPending
            )
            val playPauseAction = NotificationCompat.Action(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                if (isPlaying) "Pause" else "Play",
                playPausePending
            )
            val nextAction = NotificationCompat.Action(
                R.drawable.ic_skip_next,
                "Next",
                nextPending
            )
            val stopAction = NotificationCompat.Action(
                R.drawable.ic_close,
                "Close",
                stopPending
            )

            builder.addAction(prevAction)
            builder.addAction(playPauseAction)
            builder.addAction(nextAction)
            builder.addAction(stopAction)

            // Connect MediaSession for live seekbar scrubbing & system media controls
            val session = PlaybackViewModel.instance?.mediaSession
            if (session != null) {
                val mediaStyle = androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(session)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(stopPending)
                builder.setStyle(mediaStyle)
            }

            // On pre-Android 13, set custom glassy views
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                builder.setCustomContentView(smallViews)
                builder.setCustomBigContentView(bigViews)
            }

            return builder.build()
        }
    }
}

