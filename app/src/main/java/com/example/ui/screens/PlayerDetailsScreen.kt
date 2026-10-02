package com.example.ui.screens

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import com.example.data.AudioTrack
import com.example.player.AlbumArtType
import com.example.player.IconType
import com.example.player.PlaybackViewModel
import com.example.player.RepeatMode
import com.example.player.ReverbSystem
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.GlassBox
import com.example.ui.components.CompactPremiumDropdown
import com.example.ui.components.DropdownOption
import kotlinx.coroutines.isActive
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailsScreen(
    viewModel: PlaybackViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val position by viewModel.currentPosition.collectAsState()
    val duration by viewModel.duration.collectAsState()
    
    val iconType by viewModel.iconType.collectAsState()
    val albumArtType by viewModel.albumArtType.collectAsState()

    val repeatMode by viewModel.repeatMode.collectAsState()
    val loopStartMs by viewModel.loopStartMs.collectAsState()
    val loopEndMs by viewModel.loopEndMs.collectAsState()
    val removedArtTracks by viewModel.removedArtTrackPaths.collectAsState()

    // Local dialog & sheet states
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showLyricsOverlay by remember { mutableStateOf(false) }

    // Queue Boundary Feedback Toast / Floating Notice
    var floatingBoundaryNotice by remember { mutableStateOf<String?>(null) }
    val localContext = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.queueBoundaryEvent.collect { message ->
            floatingBoundaryNotice = message
            Toast.makeText(localContext, message, Toast.LENGTH_SHORT).show()
            delay(2200)
            if (floatingBoundaryNotice == message) {
                floatingBoundaryNotice = null
            }
        }
    }

    // Intercept system back button to close lyrics preview overlay
    androidx.activity.compose.BackHandler(enabled = showLyricsOverlay) {
        showLyricsOverlay = false
    }

    // Vinyl Motion Dynamics with realistic inertia acceleration on Play & deceleration on Pause
    var vinylAngle by remember { mutableFloatStateOf(0f) }
    val targetSpeed = if (isPlaying && albumArtType == AlbumArtType.ROTATING_VINYL) 36f else 0f
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(
            durationMillis = if (targetSpeed > 0f) 1200 else 1800,
            easing = FastOutSlowInEasing
        ),
        label = "vinyl_inertia_speed"
    )

    LaunchedEffect(animatedSpeed > 0f || targetSpeed > 0f) {
        if (animatedSpeed > 0f || targetSpeed > 0f) {
            var lastTime = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    val dt = (frameTimeNanos - lastTime) / 1_000_000_000f
                    lastTime = frameTimeNanos
                    vinylAngle = (vinylAngle + animatedSpeed * dt) % 360f
                }
            }
        }
    }

    val activeRotation = vinylAngle

    val track = currentTrack ?: return
    val isArtRemoved = removedArtTracks.contains(track.path)

    var artBitmap by remember(track.path, track.customArtPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(track.path, track.customArtPath) {
        val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (!track.customArtPath.isNullOrBlank()) {
                    val customFile = java.io.File(track.customArtPath)
                    if (customFile.exists()) {
                        android.graphics.BitmapFactory.decodeFile(track.customArtPath)
                    } else {
                        null
                    }
                } else {
                    val file = java.io.File(track.path)
                    if (file.exists()) {
                        val retriever = android.media.MediaMetadataRetriever()
                        retriever.setDataSource(track.path)
                        val artBytes = retriever.embeddedPicture
                        try {
                            retriever.release()
                        } catch (t: Throwable) {
                            // ignore release error on older APIs
                        }
                        if (artBytes != null) {
                            val options = android.graphics.BitmapFactory.Options().apply {
                                inSampleSize = 2
                            }
                            android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                        } else {
                            val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                            if (externalArtPath != null) {
                                android.graphics.BitmapFactory.decodeFile(externalArtPath)
                            } else null
                        }
                    } else {
                        null
                    }
                }
            } catch (t: Throwable) {
                null
            }
        }
        artBitmap = decoded
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
            .pointerInput(Unit) {
                var dragAccumulator = 0f
                detectDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onDragEnd = {
                        if (dragAccumulator > 150f) {
                            onClose()
                        } else if (dragAccumulator < -150f) {
                            showOptionsSheet = true
                        }
                    },
                    onDragCancel = { dragAccumulator = 0f }
                ) { change, dragAmount ->
                    change.consume()
                    dragAccumulator += dragAmount.y
                }
            },
        containerColor = Color.Transparent, // Allows parent background to show
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "NOW PLAYING",
                        fontFamily = FontFamily.Monospace,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse Player",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    // Lyrics preview button (ON THE LEFT side of loop and A/B buttons)
                    IconButton(
                        onClick = { showLyricsOverlay = true },
                        modifier = Modifier.testTag("lyrics_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = "Lyrics Preview",
                            tint = if (showLyricsOverlay) Color(0xFF00F5FF) else Color.White
                        )
                    }

                    // 4-State Playback Mode button (Normal -> Loop All -> Loop One -> Shuffle)
                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        if (repeatMode == RepeatMode.ALL) {
                            Icon(
                                painter = painterResource(com.example.R.drawable.ic_repeat_all),
                                contentDescription = "Mode: Loop All",
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = when (repeatMode) {
                                    RepeatMode.OFF -> Icons.Default.Repeat
                                    RepeatMode.ONE -> Icons.Default.RepeatOne
                                    RepeatMode.SHUFFLE -> Icons.Default.Shuffle
                                    else -> Icons.Default.Repeat
                                },
                                contentDescription = when (repeatMode) {
                                    RepeatMode.OFF -> "Mode: Normal (Stop at end)"
                                    RepeatMode.ONE -> "Mode: Loop Current Track"
                                    RepeatMode.SHUFFLE -> "Mode: Shuffle (Random)"
                                    else -> "Mode: Loop All"
                                },
                                tint = when (repeatMode) {
                                    RepeatMode.OFF -> Color.White.copy(alpha = 0.45f)
                                    else -> Color(0xFF00F5FF)
                                }
                            )
                        }
                    }
                    
                    // A/B loop button
                    IconButton(onClick = { viewModel.toggleABLoop() }) {
                        Icon(
                            imageVector = Icons.Default.Loop,
                            contentDescription = "A/B Loop",
                            tint = if (loopEndMs != null) Color(0xFF00F5FF) else if (loopStartMs != null) Color(0xFFBD00FF) else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Queue Boundary Feedback floating snackbar banner
            AnimatedVisibility(
                visible = floatingBoundaryNotice != null,
                enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { -it },
                exit = fadeOut(tween(250)) + slideOutVertically(tween(300)) { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .zIndex(25f)
            ) {
                floatingBoundaryNotice?.let { notice ->
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color(0xF20B0C16),
                        border = BorderStroke(1.dp, Color(0xFF00F5FF)),
                        shadowElevation = 10.dp,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = if (notice.contains("top", ignoreCase = true))
                                    Icons.Rounded.VerticalAlignTop
                                else
                                    Icons.Rounded.VerticalAlignBottom,
                                contentDescription = null,
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = notice,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                var totalSwipeDistanceX by remember { mutableFloatStateOf(0f) }

                // Album Art Space with fluid scale & cross-fade transitions and horizontal swipe gestures
                AnimatedContent(
                    targetState = track.path,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.88f, animationSpec = tween(300, easing = FastOutSlowInEasing))
                            + slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width / 4 }
                            + fadeIn(animationSpec = tween(280)))
                        .togetherWith(
                            scaleOut(targetScale = 0.90f, animationSpec = tween(240, easing = FastOutSlowInEasing))
                                + slideOutHorizontally(animationSpec = tween(240, easing = FastOutSlowInEasing)) { width -> -width / 4 }
                                + fadeOut(animationSpec = tween(200))
                        )
                    },
                    label = "album_art_transition",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(track.path) {
                            detectHorizontalDragGestures(
                                onDragStart = { totalSwipeDistanceX = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    totalSwipeDistanceX += dragAmount
                                },
                                onDragEnd = {
                                    val swipeThresholdPx = 45f
                                    if (totalSwipeDistanceX < -swipeThresholdPx) {
                                        // Swipe Left -> Trigger 'Next Track' playback
                                        viewModel.playNextTrack()
                                    } else if (totalSwipeDistanceX > swipeThresholdPx) {
                                        // Swipe Right -> Trigger 'Previous Track' playback
                                        viewModel.playPreviousTrack()
                                    }
                                    totalSwipeDistanceX = 0f
                                },
                                onDragCancel = { totalSwipeDistanceX = 0f }
                            )
                        }
                ) { _ ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                if (isArtRemoved) {
                    GlassBox(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        StaticAlbumArtDesign(track, artBitmap)
                    }
                } else {
                    when (albumArtType) {
                        AlbumArtType.ROUNDED -> {
                            GlassBox(
                                modifier = Modifier
                                    .size(260.dp)
                                    .clip(RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                StaticAlbumArtDesign(track, artBitmap)
                            }
                        }
                        AlbumArtType.ROTATING_VINYL -> {
                            // Futuristic Cyberpunk Vinyl Record design
                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .rotate(activeRotation),
                                contentAlignment = Alignment.Center
                            ) {
                                // Outer shiny ring
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (size.minDimension <= 0f) return@Canvas
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF1C1D30),
                                                Color(0xFF0C0D16)
                                            )
                                        ),
                                        radius = size.minDimension / 2f
                                    )
                                    // Grooves
                                    for (r in 1..8) {
                                        drawCircle(
                                            color = Color(0x15FFFFFF),
                                            radius = (size.minDimension / 2f) * (0.4f + r * 0.07f),
                                            style = Stroke(width = 1.dp.toPx())
                                        )
                                    }
                                }

                                // Middle vinyl record center
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                colors = listOf(
                                                    Color(0xFFBD00FF),
                                                    Color(0xFF00F5FF),
                                                    Color(0xFFBD00FF)
                                                )
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(82.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0B0C16)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (artBitmap != null) {
                                            Image(
                                                bitmap = artBitmap!!.asImageBitmap(),
                                                contentDescription = "Album Art Center",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.MusicNote,
                                                contentDescription = "Default Music Note Art",
                                                tint = Color(0xFF00F5FF),
                                                modifier = Modifier.size(42.dp)
                                            )
                                        }
                                    }
                                }

                                // Center core spindle hole
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF070710))
                                )
                            }
                        }
                        AlbumArtType.FLOATING_CARD -> {
                            val infiniteTransition = rememberInfiniteTransition(label = "floating_card_anim")
                            val floatOffset by infiniteTransition.animateFloat(
                                initialValue = -6f,
                                targetValue = 6f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2600, easing = FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "float_offset"
                            )
                            val tiltAngle by infiniteTransition.animateFloat(
                                initialValue = -1.2f,
                                targetValue = 1.2f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(3400, easing = FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "tilt_angle"
                            )

                            Box(
                                modifier = Modifier
                                    .size(260.dp)
                                    .offset(y = floatOffset.dp)
                                    .graphicsLayer {
                                        rotationZ = tiltAngle
                                        cameraDistance = 12f * density
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Dynamic frosted-glass shadow underneath (elevated perspective)
                                Box(
                                    modifier = Modifier
                                        .size(240.dp)
                                        .offset(y = 18.dp)
                                        .blur(22.dp)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0x9900F5FF),
                                                    Color(0x66BD00FF),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = RoundedCornerShape(32.dp)
                                        )
                                )

                                // 3D elevated floating card container with frosted-glass border
                                GlassBox(
                                    modifier = Modifier
                                        .size(260.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .border(
                                            width = 1.5.dp,
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF00F5FF).copy(alpha = 0.85f),
                                                    Color.White.copy(alpha = 0.35f),
                                                    Color(0xFFBD00FF).copy(alpha = 0.85f)
                                                )
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    StaticAlbumArtDesign(track, artBitmap)
                                }
                            }
                        }
                        AlbumArtType.AMBIENT_GLOW -> {
                            val ambientInfinite = rememberInfiniteTransition(label = "ambient_glow_pulse")
                            val glowAlpha by ambientInfinite.animateFloat(
                                initialValue = if (isPlaying) 0.55f else 0.35f,
                                targetValue = if (isPlaying) 0.85f else 0.45f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(if (isPlaying) 2000 else 3200, easing = FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "glow_alpha"
                            )
                            val glowScale by ambientInfinite.animateFloat(
                                initialValue = if (isPlaying) 1.0f else 0.98f,
                                targetValue = if (isPlaying) 1.15f else 1.02f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(if (isPlaying) 2000 else 3200, easing = FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                ),
                                label = "glow_scale"
                            )

                            Box(
                                modifier = Modifier.size(280.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Real-time reactive ambient canvas blur / glowing color aura behind artwork
                                Canvas(
                                    modifier = Modifier
                                        .size(270.dp)
                                        .graphicsLayer {
                                            scaleX = glowScale
                                            scaleY = glowScale
                                            alpha = glowAlpha
                                        }
                                        .blur(32.dp)
                                ) {
                                    if (size.minDimension <= 0f) return@Canvas
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                Color(0xFF00F5FF),
                                                Color(0xFFBD00FF),
                                                Color(0xFFFF2A6D).copy(alpha = 0.5f),
                                                Color.Transparent
                                            )
                                        ),
                                        radius = size.minDimension / 2f
                                    )
                                }

                                // Borderless rounded artwork seamlessly floating within the aura
                                Box(
                                    modifier = Modifier
                                        .size(250.dp)
                                        .clip(RoundedCornerShape(28.dp))
                                        .background(Color(0xFF0B0C16)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    StaticAlbumArtDesign(track, artBitmap)
                                }
                            }
                        }
                    }
                }
                }
            }

            // Track details card with fluid metadata transitions and animated Favorite Button
            val favStats by viewModel.favoriteStats.collectAsState()
            val isFav = favStats.any { it.trackPath == track.path && it.isFavorite }
            val favInteraction = remember { MutableInteractionSource() }
            val isFavPressed by favInteraction.collectIsPressedAsState()
            val heartScale by animateFloatAsState(
                targetValue = if (isFavPressed) 0.80f else if (isFav) 1.25f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "heart_scale"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnimatedContent(
                    targetState = track.path,
                    transitionSpec = {
                        (slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { width -> width / 4 } + fadeIn(animationSpec = tween(250)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) { width -> -width / 4 } + fadeOut(animationSpec = tween(180)))
                    },
                    label = "track_metadata_transition",
                    modifier = Modifier.weight(1f)
                ) { _ ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = track.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = track.artist,
                                fontSize = 14.sp,
                                color = Color(0xFF00F5FF),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            // Glassy audio format tag
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x1FADAEB5))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = track.format,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Favorite Heart Button with micro-bounce
                IconButton(
                    onClick = { viewModel.toggleFavorite(track.path) },
                    interactionSource = favInteraction,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("player_favorite_button")
                        .scale(heartScale)
                        .clip(CircleShape)
                        .background(if (isFav) Color(0x26FF2A6D) else Color(0x14FFFFFF))
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                        tint = if (isFav) Color(0xFFFF2A6D) else Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Standard Slider block
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = {
                        viewModel.seekTo((it * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = Color(0xFF00F5FF),
                        inactiveTrackColor = Color(0x3300F5FF),
                        thumbColor = Color(0xFF00F5FF)
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("player_seek_slider")
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatTime(position),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    // A/B loop simple status label
                    if (loopStartMs != null) {
                        val label = if (loopEndMs != null) {
                            "A-B LOOP ACTIVE"
                        } else {
                            "A SET AT ${formatTime(loopStartMs!!)}"
                        }
                        Text(
                            text = label,
                            color = Color(0xFF00F5FF),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    Text(
                        formatTime(duration),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Main playback controls aligned with standard system control type selection
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev button with micro-bounce
                val prevInteraction = remember { MutableInteractionSource() }
                val isPrevPressed by prevInteraction.collectIsPressedAsState()
                val prevScale by animateFloatAsState(
                    targetValue = if (isPrevPressed) 0.82f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "prev_press_scale"
                )

                IconButton(
                    onClick = { viewModel.playPreviousTrack() },
                    interactionSource = prevInteraction,
                    modifier = Modifier
                        .size(56.dp)
                        .scale(prevScale)
                        .testTag("player_prev_button")
                ) {
                    PlayerCustomIcon(
                        iconType = iconType,
                        filled = Icons.Filled.SkipPrevious,
                        outlined = Icons.Outlined.SkipPrevious,
                        rounded = Icons.Rounded.SkipPrevious,
                        color = Color.White,
                        size = 32
                    )
                }

                // Play / Pause glowing neon key button with micro-bounce
                val playInteraction = remember { MutableInteractionSource() }
                val isPlayPressed by playInteraction.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.88f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "play_press_scale"
                )

                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .scale(playScale)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFBD00FF), Color(0xFF00F5FF))
                            )
                        )
                        .clickable(
                            interactionSource = playInteraction,
                            indication = null
                        ) { viewModel.togglePlayPause() }
                        .padding(2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(Color(0xFF0B0C16)),
                        contentAlignment = Alignment.Center
                    ) {
                        PlayerCustomIcon(
                            iconType = iconType,
                            filled = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            outlined = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            rounded = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            color = if (iconType == IconType.CYBERPUNK) Color(0xFF00F5FF) else Color.White,
                            size = 36
                        )
                    }
                }

                // Next button with micro-bounce
                val nextInteraction = remember { MutableInteractionSource() }
                val isNextPressed by nextInteraction.collectIsPressedAsState()
                val nextScale by animateFloatAsState(
                    targetValue = if (isNextPressed) 0.82f else 1.0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "next_press_scale"
                )

                IconButton(
                    onClick = { viewModel.playNextTrack() },
                    interactionSource = nextInteraction,
                    modifier = Modifier
                        .size(56.dp)
                        .scale(nextScale)
                        .testTag("player_next_button")
                ) {
                    PlayerCustomIcon(
                        iconType = iconType,
                        filled = Icons.Filled.SkipNext,
                        outlined = Icons.Outlined.SkipNext,
                        rounded = Icons.Rounded.SkipNext,
                        color = Color.White,
                        size = 32
                    )
                }
            }

            // Bottom subtle instruction
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { showOptionsSheet = true }
                    .padding(vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Swipe up for options",
                    tint = Color.LightGray.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Swipe up for options",
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
    }

    // Fullscreen Opaque Gradient Blur Lyrics Overlay
    if (showLyricsOverlay) {
        val currentLyrics by viewModel.currentLyrics.collectAsState()
        val isLoadingLyrics by viewModel.isLoadingLyrics.collectAsState()

        LaunchedEffect(track.path) {
            viewModel.loadLyricsForCurrentTrack()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(200f)
                .background(Color(0xFF07080D)) // 100% Opaque base: completely hides underlying controls and text
                .pointerInput(Unit) {
                    detectTapGestures { /* Swallow taps to block underlying clickability */ }
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Intercept all touches */ }
                )
        ) {
            // Gradient Blur Background ambient effects
            // Glowing cyan ambient blur orb
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .offset(x = (-60).dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF00F5FF).copy(alpha = 0.35f),
                                Color(0xFF00F5FF).copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .blur(50.dp)
            )

            // Glowing purple/magenta ambient blur orb
            Box(
                modifier = Modifier
                    .size(380.dp)
                    .align(Alignment.CenterEnd)
                    .offset(x = 80.dp, y = 40.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFBD00FF).copy(alpha = 0.35f),
                                Color(0xFFBD00FF).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
                    .blur(60.dp)
            )

            // Ambient vertical studio vignette
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC07080D),
                                Color(0x9907080D),
                                Color(0xEE07080D)
                            )
                        )
                    )
            )

            // Frosted Lyrics Card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(14.dp)
            ) {
                // Header Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp)),
                    color = Color(0x33121524),
                    border = BorderStroke(1.dp, Color(0xFF00F5FF).copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Cross icon on TOP LEFT to close lyrics preview
                        IconButton(
                            onClick = { showLyricsOverlay = false },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.6f), CircleShape)
                                .testTag("close_lyrics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Lyrics",
                                tint = Color.White
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "TIME-SYNCED LYRICS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F5FF),
                                fontSize = 12.sp,
                                letterSpacing = 2.sp
                            )
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Reload lyrics button on top right
                        IconButton(
                            onClick = { viewModel.loadLyricsForCurrentTrack() },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF))
                                .testTag("refresh_lyrics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload Lyrics",
                                tint = Color(0xFF00F5FF)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main lyrics container card with glowing border and frosted dark interior
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xD90E111C),
                    border = BorderStroke(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF00F5FF).copy(alpha = 0.75f),
                                Color(0xFFBD00FF).copy(alpha = 0.75f)
                            )
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        com.example.ui.components.TimeSyncedLyricsView(
                            lyricsResult = currentLyrics,
                            currentPositionMs = position,
                            isPlaying = isPlaying,
                            isLoading = isLoadingLyrics,
                            onSeekTo = { targetMs ->
                                viewModel.seekTo(targetMs)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

    // Modal bottom sheet containing options with smooth scale-fade animation
    if (showOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showOptionsSheet = false },
            sheetState = sheetState,
            containerColor = Color(0xF20F101A),
            scrimColor = Color.Black.copy(alpha = 0.72f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF00F5FF), Color(0xFFBD00FF))
                                )
                            )
                    )
                }
            }
        ) {
            val context = LocalContext.current
            val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            var currentVol by remember { mutableStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }

            val tempo by viewModel.tempo.collectAsState()
            val pitch by viewModel.pitch.collectAsState()
            val reverbLevel by viewModel.reverbLevel.collectAsState()

            var contentVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                contentVisible = true
            }

            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn(tween(250)) + scaleIn(initialScale = 0.94f, animationSpec = tween(280, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                // Title
                Text(
                    text = "ONGAKU OPTIONS",
                    color = Color(0xFF00F5FF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                // Playlist & Favorite Quick Actions
                currentTrack?.let { track ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val favStats by viewModel.favoriteStats.collectAsState()
                        val isFav = favStats.any { it.trackPath == track.path && it.isFavorite }

                        OutlinedButton(
                            onClick = { viewModel.toggleFavorite(track.path) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isFav) Color(0xFFFF2A6D) else Color.White
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = Brush.horizontalGradient(
                                    if (isFav) listOf(Color(0xFFFF2A6D), Color(0xFFFF758C))
                                    else listOf(Color(0x55FFFFFF), Color(0x22FFFFFF))
                                )
                            )
                        ) {
                            Icon(
                                imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFav) Color(0xFFFF2A6D) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isFav) "FAVORITED" else "FAVORITE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                showOptionsSheet = false
                                showAddToPlaylistDialog = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x2200F5FF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "PLAYLIST",
                                color = Color(0xFF00F5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // 1. Volume Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Volume",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Volume Control",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Slider(
                        value = currentVol,
                        onValueChange = {
                            currentVol = it
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.toInt(), 0)
                        },
                        valueRange = 0f..maxVol,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFF00F5FF),
                            inactiveTrackColor = Color.DarkGray,
                            thumbColor = Color(0xFF00F5FF)
                        )
                    )
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 2. Information Section with Edit Metadata Pen
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Information",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Information & Metadata",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        // Edit metadata icon (pen)
                        IconButton(
                            onClick = {
                                showEditDialog = true
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Metadata",
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Title: ${track.title}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Artist: ${track.artist}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Album: ${track.album}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Format: ${track.format}", color = Color.LightGray, fontSize = 12.sp)
                    Text("Path: ${track.path}", color = Color.LightGray, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 3. Tempo & Pitch Section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Tempo & Pitch",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tempo & Pitch Rate",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Tempo rate with Reset Option
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tempo Rate: ${String.format(Locale.getDefault(), "%.2f", tempo)}x",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        if (tempo != 1.0f) {
                            Text(
                                text = "Reset to 1.00x",
                                color = Color(0xFF00F5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.setTempo(1.0f) }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { 
                                val newTempo = ((Math.round((tempo - 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
                                viewModel.setTempo(newTempo) 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease Tempo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Slider(
                            value = tempo,
                            onValueChange = { viewModel.setTempo(it.coerceIn(0.1f, 2.0f)) },
                            valueRange = 0.0f..2.0f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF00F5FF),
                                inactiveTrackColor = Color.DarkGray,
                                thumbColor = Color(0xFF00F5FF)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { 
                                val newTempo = ((Math.round((tempo + 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
                                viewModel.setTempo(newTempo) 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase Tempo",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Pitch rate with Reset Option
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Pitch Rate: ${String.format(Locale.getDefault(), "%.2f", pitch)}x",
                            color = Color.LightGray,
                            fontSize = 12.sp
                        )
                        if (pitch != 1.0f) {
                            Text(
                                text = "Reset to 1.00x",
                                color = Color(0xFFBD00FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.setPitch(1.0f) }
                                    .padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { 
                                val newPitch = ((Math.round((pitch - 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
                                viewModel.setPitch(newPitch) 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = "Decrease Pitch",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Slider(
                            value = pitch,
                            onValueChange = { viewModel.setPitch(it.coerceIn(0.1f, 2.0f)) },
                            valueRange = 0.0f..2.0f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFFBD00FF),
                                inactiveTrackColor = Color.DarkGray,
                                thumbColor = Color(0xFFBD00FF)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(
                            onClick = { 
                                val newPitch = ((Math.round((pitch + 0.1f) * 10f)) / 10f).coerceIn(0.1f, 2.0f)
                                viewModel.setPitch(newPitch) 
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Increase Pitch",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 4. Reverb Section with System Dropdown
                val reverbSystem by viewModel.reverbSystem.collectAsState()
                val reverbHz by viewModel.reverbHz.collectAsState()
                val bassBoostStrength by viewModel.bassBoostStrength.collectAsState()

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Reverb",
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Acoustic Reverb",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Premium Dropdown to choose reverb system: Hz or Room
                        CompactPremiumDropdown(
                            selectedOption = reverbSystem,
                            options = listOf(
                                DropdownOption(ReverbSystem.ROOM, "Room Preset"),
                                DropdownOption(ReverbSystem.HZ, "Hz Frequency")
                            ),
                            onOptionSelected = { viewModel.setReverbSystem(it) },
                            accentColor = Color(0xFF00F5FF)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (reverbSystem == ReverbSystem.ROOM) {
                        val presetName = when (reverbLevel.toInt()) {
                            0 -> "None (Bypass)"
                            1 -> "Small Room (Warm)"
                            2 -> "Medium Room (Studio)"
                            3 -> "Large Room (Stage)"
                            4 -> "Medium Hall (Club)"
                            5 -> "Large Hall (Cathedral)"
                            6 -> "Plate (Bright Shimmer)"
                            else -> "None"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Room Architecture",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = presetName,
                                color = Color(0xFF00F5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = reverbLevel,
                            onValueChange = { viewModel.setReverbLevel(it) },
                            valueRange = 0f..6f,
                            steps = 5,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF00F5FF),
                                inactiveTrackColor = Color(0x3300F5FF),
                                thumbColor = Color(0xFF00F5FF)
                            )
                        )
                    } else {
                        val formattedHz = when {
                            reverbHz <= 0f -> "0 Hz (Off)"
                            reverbHz >= 1000f -> String.format(java.util.Locale.US, "%.1f kHz", reverbHz / 1000f)
                            else -> "${reverbHz.toInt()} Hz"
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Acoustic Reflection Frequency",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (reverbHz > 0f) {
                                    Text(
                                        text = "Reset to 0 Hz",
                                        color = Color(0xFF00F5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { viewModel.setReverbHz(0f) }
                                            .padding(end = 8.dp)
                                    )
                                }
                                Text(
                                    text = formattedHz,
                                    color = Color(0xFF00F5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = reverbHz,
                            onValueChange = { viewModel.setReverbHz(it) },
                            valueRange = 0f..10000f,
                            colors = SliderDefaults.colors(
                                activeTrackColor = Color(0xFF00F5FF),
                                inactiveTrackColor = Color(0x3300F5FF),
                                thumbColor = Color(0xFF00F5FF)
                            )
                        )
                    }
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 4b. Bass Boost Section (Red color slider)
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Bass Boost",
                                tint = Color(0xFFFF1744),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Subwoofer Bass Boost",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        val boostPercent = (bassBoostStrength / 10).toInt()
                        Text(
                            text = if (boostPercent == 0) "OFF" else "+$boostPercent%",
                            color = Color(0xFFFF1744),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = bassBoostStrength,
                        onValueChange = { viewModel.setBassBoostStrength(it) },
                        valueRange = 0f..1000f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFFFF1744),
                            inactiveTrackColor = Color(0x33FF1744),
                            thumbColor = Color(0xFFFF1744)
                        )
                    )
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 4b. Equalizer Section
                val eqEnabled by viewModel.eqEnabled.collectAsState()
                val eqBands by viewModel.eqBands.collectAsState()

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "5-Band Equalizer",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Switch(
                            checked = eqEnabled,
                            onCheckedChange = { viewModel.setEqEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00F5FF),
                                checkedTrackColor = Color(0xFF00F5FF).copy(alpha = 0.5f)
                            )
                        )
                    }

                    if (eqEnabled) {
                        val freqLabels = listOf("60Hz (Bass)", "230Hz", "910Hz", "4kHz", "14kHz (Treble)")
                        Spacer(modifier = Modifier.height(8.dp))
                        eqBands.forEachIndexed { index, value ->
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = freqLabels.getOrElse(index) { "Band ${index + 1}" },
                                        color = Color.LightGray,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${String.format(Locale.getDefault(), "%+.1f", value)} dB",
                                        color = Color(0xFF00F5FF),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    IconButton(
                                        onClick = { viewModel.setEqBand(index, value - 0.5f) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Remove,
                                            contentDescription = "Decrease Band Level",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Slider(
                                        value = value,
                                        onValueChange = { viewModel.setEqBand(index, it) },
                                        valueRange = -15f..15f,
                                        colors = SliderDefaults.colors(
                                            activeTrackColor = Color(0xFF00F5FF),
                                            inactiveTrackColor = Color.DarkGray,
                                            thumbColor = Color(0xFF00F5FF)
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.setEqBand(index, value + 0.5f) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Add,
                                            contentDescription = "Increase Band Level",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 4c. 8D Audio Section (WebAudio API LFO Architecture)
                val eightDEnabled by viewModel.eightDEnabled.collectAsState()
                val eightDSpeed by viewModel.eightDSpeed.collectAsState()
                val eightDDepth by viewModel.eightDDepth.collectAsState()
                val eightDPreset by viewModel.eightDPreset.collectAsState()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF14121F))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (eightDEnabled) Brush.linearGradient(listOf(Color(0xFFBD00FF), Color(0xFF00F5FF)))
                                        else Brush.linearGradient(listOf(Color(0x33BD00FF), Color(0x3300F5FF)))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = "8D Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "8D REALTIME SPATIAL ENGINE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "WebAudio LFO Panning & Reverb",
                                    color = Color(0xFF00F5FF),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.setEightDEnabled(!eightDEnabled)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (eightDEnabled) Color(0xFFBD00FF) else Color(0x22FFFFFF)
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("playBtn")
                        ) {
                            Text(
                                text = if (eightDEnabled) "■ STOP 8D" else "▶ PLAY 8D",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (eightDEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))

                        // Presets
                        Text(
                            text = "PRESETS",
                            color = Color.LightGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val presetList = listOf(
                                "classic" to "Classic",
                                "gentle" to "Gentle",
                                "intense" to "Intense",
                                "dreamy" to "Dreamy"
                            )
                            presetList.forEach { (key, label) ->
                                val isSelected = eightDPreset == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) Brush.linearGradient(listOf(Color(0xFFBD00FF), Color(0xFF00F5FF)))
                                            else Brush.linearGradient(listOf(Color(0x15FFFFFF), Color(0x15FFFFFF)))
                                        )
                                        .clickable {
                                            viewModel.setEightDPreset(key)
                                        }
                                        .padding(vertical = 8.dp, horizontal = 2.dp)
                                        .testTag("preset-btn")
                                        .testTag("preset_$key"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xCCFFFFFF),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Rotation Speed
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ROTATION SPEED (LFO)",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${String.format(Locale.getDefault(), "%.2f", eightDSpeed)} Hz",
                                    color = Color(0xFF00F5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("speedVal")
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { viewModel.setEightDSpeed((eightDSpeed - 0.02f).coerceIn(0.01f, 1.0f)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Decrease Rotation Speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Slider(
                                    value = eightDSpeed,
                                    onValueChange = { viewModel.setEightDSpeed(it) },
                                    valueRange = 0.01f..1.0f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0xFF00F5FF),
                                        inactiveTrackColor = Color(0x33FFFFFF),
                                        thumbColor = Color(0xFF00F5FF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("rotationSpeed")
                                )
                                IconButton(
                                    onClick = { viewModel.setEightDSpeed((eightDSpeed + 0.02f).coerceIn(0.01f, 1.0f)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Increase Rotation Speed",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Rotation Depth
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "ROTATION DEPTH (STEREO)",
                                    color = Color.LightGray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${eightDDepth.toInt()}%",
                                    color = Color(0xFFBD00FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.testTag("depthVal")
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                IconButton(
                                    onClick = { viewModel.setEightDDepth((eightDDepth - 5f).coerceIn(0f, 100f)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Remove,
                                        contentDescription = "Decrease Depth",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Slider(
                                    value = eightDDepth,
                                    onValueChange = { viewModel.setEightDDepth(it) },
                                    valueRange = 0f..100f,
                                    colors = SliderDefaults.colors(
                                        activeTrackColor = Color(0xFFBD00FF),
                                        inactiveTrackColor = Color(0x33FFFFFF),
                                        thumbColor = Color(0xFFBD00FF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("rotationDepth")
                                )
                                IconButton(
                                    onClick = { viewModel.setEightDDepth((eightDDepth + 5f).coerceIn(0f, 100f)) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Increase Depth",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Divider
                Divider(color = Color.White.copy(alpha = 0.1f))

                // 5. Rename Section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showRenameDialog = true
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BorderColor,
                        contentDescription = "Rename",
                        tint = Color(0xFF00F5FF),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Rename Music File",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            }
        }
    }

    // Dialog: Edit Music Metadata
    if (showEditDialog) {
        val context = LocalContext.current
        var tempTitle by remember { mutableStateOf(track.title) }
        var tempArtist by remember { mutableStateOf(track.artist) }
        var tempAlbum by remember { mutableStateOf(track.album) }
        var tempGenre by remember { mutableStateOf(track.genre) }
        var tempYear by remember { mutableStateOf(track.year) }
        var tempTrackNum by remember { mutableStateOf(track.trackNumber) }
        var tempDiscNum by remember { mutableStateOf(track.discNumber) }
        var tempComment by remember { mutableStateOf(track.comment) }
        var tempCustomArtPath by remember { mutableStateOf(track.customArtPath) }
        var artRemoved by remember { mutableStateOf(isArtRemoved) }
        
        val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri: android.net.Uri? ->
            if (uri != null) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream != null) {
                        val customArtDir = java.io.File(context.filesDir, "custom_arts")
                        if (!customArtDir.exists()) {
                            customArtDir.mkdirs()
                        }
                        val pathHash = track.path.hashCode().toString()
                        val targetFile = java.io.File(customArtDir, "custom_art_$pathHash.jpg")
                        val outputStream = java.io.FileOutputStream(targetFile)
                        inputStream.copyTo(outputStream)
                        inputStream.close()
                        outputStream.close()
                        tempCustomArtPath = targetFile.absolutePath
                        artRemoved = false
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MetadataEditor", "Error copying image: ${e.message}")
                }
            }
        }
        
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Music Metadata", color = Color.White) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    OutlinedTextField(
                        value = tempTitle,
                        onValueChange = { tempTitle = it },
                        label = { Text("Title") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00F5FF),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempArtist,
                        onValueChange = { tempArtist = it },
                        label = { Text("Artist") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00F5FF),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempAlbum,
                        onValueChange = { tempAlbum = it },
                        label = { Text("Album") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00F5FF),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempGenre,
                        onValueChange = { tempGenre = it },
                        label = { Text("Genre") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00F5FF),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempYear,
                        onValueChange = { tempYear = it },
                        label = { Text("Year") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00F5FF),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = tempTrackNum,
                            onValueChange = { tempTrackNum = it },
                            label = { Text("Track #") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00F5FF),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF00F5FF),
                                unfocusedLabelColor = Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tempDiscNum,
                            onValueChange = { tempDiscNum = it },
                            label = { Text("Disc #") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00F5FF),
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color(0xFF00F5FF),
                                unfocusedLabelColor = Color.Gray
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = tempComment,
                        onValueChange = { tempComment = it },
                        label = { Text("Comment") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00F5FF),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFF00F5FF),
                            unfocusedLabelColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "ALBUM ARTWORK",
                        color = Color(0xFF00F5FF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!artRemoved) {
                                var previewBitmap by remember(tempCustomArtPath) { mutableStateOf<android.graphics.Bitmap?>(null) }
                                LaunchedEffect(tempCustomArtPath) {
                                    val decoded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            if (!tempCustomArtPath.isNullOrBlank()) {
                                                android.graphics.BitmapFactory.decodeFile(tempCustomArtPath)
                                            } else {
                                                val file = java.io.File(track.path)
                                                if (file.exists()) {
                                                    val retriever = android.media.MediaMetadataRetriever()
                                                    retriever.setDataSource(track.path)
                                                    val artBytes = retriever.embeddedPicture
                                                    try {
                                                        retriever.release()
                                                    } catch (e: Exception) {}
                                                    if (artBytes != null) {
                                                        android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
                                                    } else null
                                                } else null
                                            }
                                        } catch (t: Throwable) {
                                            null
                                        }
                                    }
                                    previewBitmap = decoded
                                }
                                if (previewBitmap != null) {
                                    Image(
                                        bitmap = previewBitmap!!.asImageBitmap(),
                                        contentDescription = "Art preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.LightGray)
                                }
                            } else {
                                Icon(Icons.Rounded.ImageNotSupported, contentDescription = null, tint = Color.Gray)
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Button(
                                onClick = { pickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1A00F5FF)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00F5FF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Choose Image", color = Color(0xFF00F5FF), fontSize = 12.sp)
                            }
                            
                            if (tempCustomArtPath != null) {
                                TextButton(
                                    onClick = { tempCustomArtPath = null },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Reset to default", color = Color.Red, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { artRemoved = !artRemoved }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = artRemoved,
                            onCheckedChange = { artRemoved = it },
                            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF00F5FF))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Thumbnail Album Art", color = Color.White, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateTrackMetadata(
                            trackPath = track.path,
                            newTitle = tempTitle,
                            newArtist = tempArtist,
                            newAlbum = tempAlbum,
                            newGenre = tempGenre,
                            newYear = tempYear,
                            newTrackNumber = tempTrackNum,
                            newDiscNumber = tempDiscNum,
                            newComment = tempComment,
                            newCustomArtPath = tempCustomArtPath,
                            removeArt = artRemoved
                        )
                        showEditDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF))
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF131424)
        )
    }

    // Dialog: Rename File
    if (showRenameDialog) {
        var tempFileName by remember { mutableStateOf(File(track.path).nameWithoutExtension) }
        
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Music File", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = tempFileName,
                    onValueChange = { tempFileName = it },
                    label = { Text("New File Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color(0xFF00F5FF),
                        unfocusedLabelColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameTrackFile(track, tempFileName)
                        showRenameDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF))
                ) {
                    Text("Rename", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF131424)
        )
    }

    if (showAddToPlaylistDialog && currentTrack != null) {
        AddToPlaylistDialog(
            track = currentTrack!!,
            viewModel = viewModel,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }
}

@Composable
fun StaticAlbumArtDesign(track: AudioTrack, artBitmap: android.graphics.Bitmap?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (artBitmap != null) {
            Image(
                bitmap = artBitmap.asImageBitmap(),
                contentDescription = "Album Art Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Decorative background patterns
            Canvas(modifier = Modifier.fillMaxSize()) {
                if (size.minDimension <= 0f) return@Canvas
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3300F5FF), Color(0x00000000))
                    ),
                    radius = size.minDimension / 2f
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = "Launcher Icon Default Art",
                    tint = Color(0xFF00F5FF),
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = track.album,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Handles mapping and drawing the icons depending on the selected Icon Type selector settings.
 */
@Composable
fun PlayerCustomIcon(
    iconType: IconType,
    filled: ImageVector,
    outlined: ImageVector,
    rounded: ImageVector,
    color: Color,
    size: Int
) {
    val activeIcon = when (iconType) {
        IconType.FILLED -> filled
        IconType.OUTLINED -> outlined
        IconType.ROUNDED -> rounded
        IconType.CYBERPUNK -> filled // Cyberpunk matches filled but uses custom neon glowing filters
    }

    val finalColor = if (iconType == IconType.CYBERPUNK) {
        Color(0xFF00F5FF)
    } else {
        color
    }

    Icon(
        imageVector = activeIcon,
        contentDescription = null,
        tint = finalColor,
        modifier = Modifier.size(size.dp)
    )
}

/**
 * Helper to format audio durations.
 */
fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
