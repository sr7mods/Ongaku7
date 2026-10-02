package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.os.Build
import android.widget.RemoteViews
import androidx.palette.graphics.Palette
import com.example.MainActivity
import com.example.R
import com.example.data.AudioTrack
import com.example.player.MediaPlaybackService
import com.example.player.PlaybackViewModel
import java.io.File

class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            try {
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, PlayerWidgetProvider::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                if (appWidgetIds != null && appWidgetIds.isNotEmpty()) {
                    for (widgetId in appWidgetIds) {
                        updateWidget(context, appWidgetManager, widgetId)
                    }
                }
            } catch (e: Exception) {
                // Ignore background race conditions
            }
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_player_glass)

            val vm = PlaybackViewModel.instance
            val track: AudioTrack? = vm?.currentTrack?.value
            val isPlaying = vm?.isPlaying?.value ?: false
            val position = vm?.currentPosition?.value ?: 0L
            val duration = vm?.duration?.value ?: 0L
            val isFav = if (track != null && vm != null) vm.isFavorite(track.path) else false

            // Set track title & artist
            if (track != null) {
                views.setTextViewText(R.id.widget_track_title, track.title)
                val artistAlbum = if (track.album.isNotBlank() && track.album != "Unknown Album") {
                    "${track.artist}  •  ${track.album}"
                } else {
                    track.artist
                }
                views.setTextViewText(R.id.widget_track_artist, artistAlbum)

                // Progress bar
                val progressPercent = if (duration > 0) ((position * 100) / duration).toInt() else 0
                views.setProgressBar(R.id.widget_progress, 100, progressPercent, false)
            } else {
                views.setTextViewText(R.id.widget_track_title, "Ongaku7")
                views.setTextViewText(R.id.widget_track_artist, "Tap to start listening")
                views.setProgressBar(R.id.widget_progress, 100, 0, false)
            }

            // Play/pause button icon & tint
            views.setImageViewResource(
                R.id.widget_btn_play_pause,
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )

            // Play status badge on art
            views.setImageViewResource(
                R.id.widget_play_badge,
                if (isPlaying) R.drawable.ic_play_arrow else R.drawable.ic_pause
            )

            // Favorite toggle
            if (isFav) {
                views.setImageViewResource(R.id.widget_btn_fav, R.drawable.ic_favorite)
                views.setInt(R.id.widget_btn_fav, "setColorFilter", 0xFFFF2A6D.toInt())
            } else {
                views.setImageViewResource(R.id.widget_btn_fav, R.drawable.ic_favorite_border)
                views.setInt(R.id.widget_btn_fav, "setColorFilter", 0xFFFFFFFF.toInt())
            }

            // Load album art & extract dynamic ambient palette glow
            val artBitmap = loadAlbumArtBitmap(track)
            if (artBitmap != null) {
                val roundedArt = createRoundedBitmap(artBitmap, 24f)
                views.setImageViewBitmap(R.id.widget_album_art, roundedArt)

                // Palette API color extraction
                try {
                    val palette = Palette.from(artBitmap).generate()
                    val dominantColor = palette.getVibrantColor(
                        palette.getDominantColor(0xFF00F5FF.toInt())
                    )
                    val glowBitmap = createAmbientGlowBitmap(dominantColor, 480, 140)
                    views.setImageViewBitmap(R.id.widget_ambient_glow, glowBitmap)
                } catch (e: Exception) {
                    views.setImageViewBitmap(R.id.widget_ambient_glow, null)
                }
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_play_arrow)
                views.setImageViewBitmap(R.id.widget_ambient_glow, null)
            }

            // Tap on Art or Title opens main app player view
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_player", true)
            }
            val openAppPending = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_art_container, openAppPending)
            views.setOnClickPendingIntent(R.id.widget_info_container, openAppPending)

            // Media control button actions sent directly to MediaPlaybackService
            val prevIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_PREVIOUS
            }
            val prevPending = PendingIntent.getService(
                context, 101, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)

            val playPauseIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_PLAY_PAUSE
            }
            val playPausePending = PendingIntent.getService(
                context, 102, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, playPausePending)

            val nextIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_NEXT
            }
            val nextPending = PendingIntent.getService(
                context, 103, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

            val favIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_TOGGLE_FAVORITE
            }
            val favPending = PendingIntent.getService(
                context, 104, favIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_fav, favPending)

            // Loop / Repeat toggle
            val repeatMode = vm?.repeatMode?.value ?: com.example.player.RepeatMode.OFF
            val loopIcon = when (repeatMode) {
                com.example.player.RepeatMode.OFF -> R.drawable.ic_repeat
                com.example.player.RepeatMode.ALL -> R.drawable.ic_repeat_all
                com.example.player.RepeatMode.ONE -> R.drawable.ic_repeat_one
                com.example.player.RepeatMode.SHUFFLE -> R.drawable.ic_shuffle
            }
            val loopColor = when (repeatMode) {
                com.example.player.RepeatMode.OFF -> 0xFF888888.toInt()
                com.example.player.RepeatMode.ALL -> 0xFF00F5FF.toInt()
                com.example.player.RepeatMode.ONE -> 0xFF00F5FF.toInt()
                com.example.player.RepeatMode.SHUFFLE -> 0xFFBD00FF.toInt()
            }
            views.setImageViewResource(R.id.widget_btn_loop, loopIcon)
            views.setInt(R.id.widget_btn_loop, "setColorFilter", loopColor)

            val loopIntent = Intent(context, MediaPlaybackService::class.java).apply {
                action = MediaPlaybackService.ACTION_TOGGLE_LOOP
            }
            val loopPending = PendingIntent.getService(
                context, 105, loopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_loop, loopPending)

            // Open app / library button
            views.setOnClickPendingIntent(R.id.widget_btn_library, openAppPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        private fun loadAlbumArtBitmap(track: AudioTrack?): Bitmap? {
            if (track == null) return null
            return try {
                if (!track.customArtPath.isNullOrBlank() && File(track.customArtPath).exists()) {
                    BitmapFactory.decodeFile(track.customArtPath)
                } else {
                    val file = File(track.path)
                    if (file.exists()) {
                        val retriever = MediaMetadataRetriever()
                        retriever.setDataSource(track.path)
                        val artBytes = retriever.embeddedPicture
                        try { retriever.release() } catch (t: Throwable) {}
                        if (artBytes != null) {
                            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                            BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                        } else {
                            val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                            if (externalArtPath != null) {
                                BitmapFactory.decodeFile(externalArtPath)
                            } else null
                        }
                    } else null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun createRoundedBitmap(src: Bitmap, cornerRadius: Float): Bitmap {
            val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, src.width, src.height)
            val rectF = RectF(rect)
            canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(src, rect, rect, paint)
            return output
        }

        private fun createAmbientGlowBitmap(color: Int, width: Int, height: Int): Bitmap {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val gradient = RadialGradient(
                width * 0.2f, height * 0.5f,
                width * 0.75f,
                intArrayOf(color, color and 0x00FFFFFF),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            paint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            return bitmap
        }
    }
}
