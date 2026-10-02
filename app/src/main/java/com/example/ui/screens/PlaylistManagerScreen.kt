package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AudioTrack
import com.example.data.playlist.PlaylistEntity
import com.example.data.playlist.PlaylistTrackEntity
import com.example.data.playlist.SmartPlaylistType
import com.example.player.PlaybackViewModel
import com.example.ui.components.GlassBox
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

// Sealed class for active view in Playlist Manager
sealed class PlaylistViewMode {
    data object Overview : PlaylistViewMode()
    data class CustomDetail(val playlist: PlaylistEntity) : PlaylistViewMode()
    data class SmartDetail(val type: SmartPlaylistType) : PlaylistViewMode()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistManagerScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf<PlaylistViewMode>(PlaylistViewMode.Overview) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }

    // Intercept back button when inside a playlist detail
    BackHandler {
        if (viewMode != PlaylistViewMode.Overview) {
            viewMode = PlaylistViewMode.Overview
        } else {
            onBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (val mode = viewMode) {
                            is PlaylistViewMode.Overview -> "PLAYLISTS"
                            is PlaylistViewMode.CustomDetail -> mode.playlist.title.uppercase()
                            is PlaylistViewMode.SmartDetail -> when (mode.type) {
                                SmartPlaylistType.FAVORITES -> "FAVORITES"
                                SmartPlaylistType.RECENTLY_ADDED -> "RECENTLY ADDED"
                                SmartPlaylistType.MOST_PLAYED -> "MOST PLAYED"
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (viewMode != PlaylistViewMode.Overview) {
                                viewMode = PlaylistViewMode.Overview
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.testTag("playlist_nav_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF00F5FF)
                        )
                    }
                },
                actions = {
                    if (viewMode is PlaylistViewMode.Overview) {
                        IconButton(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier
                                .testTag("create_playlist_header_button")
                                .clip(CircleShape)
                                .background(Color(0x1A00F5FF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Create Playlist",
                                tint = Color(0xFF00F5FF)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x1F000000)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    if (targetState != PlaylistViewMode.Overview) {
                        (slideInHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> fullWidth }
                        ) + fadeIn(tween(300)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> -fullWidth / 3 }
                            ) + fadeOut(tween(200))
                        )
                    } else {
                        (slideInHorizontally(
                            animationSpec = tween(350, easing = FastOutSlowInEasing),
                            initialOffsetX = { fullWidth -> -fullWidth / 3 }
                        ) + fadeIn(tween(300)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(300, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> fullWidth }
                            ) + fadeOut(tween(200))
                        )
                    }
                },
                label = "PlaylistViewTransition"
            ) { mode ->
                when (mode) {
                    is PlaylistViewMode.Overview -> {
                        PlaylistOverviewView(
                            playlists = playlists,
                            viewModel = viewModel,
                            onOpenCustom = { viewMode = PlaylistViewMode.CustomDetail(it) },
                            onOpenSmart = { viewMode = PlaylistViewMode.SmartDetail(it) },
                            onCreateClick = { showCreateDialog = true }
                        )
                    }
                    is PlaylistViewMode.CustomDetail -> {
                        // Find latest entity from DB list or fallback to passed entity
                        val latestEntity = playlists.find { it.id == mode.playlist.id } ?: mode.playlist
                        CustomPlaylistDetailView(
                            playlist = latestEntity,
                            viewModel = viewModel,
                            onBackToOverview = { viewMode = PlaylistViewMode.Overview }
                        )
                    }
                    is PlaylistViewMode.SmartDetail -> {
                        SmartPlaylistDetailView(
                            type = mode.type,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Floating Mini-Player at bottom
            AnimatedVisibility(
                visible = currentTrack != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                currentTrack?.let { track ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onMiniPlayerClick() }
                            .testTag("playlist_floating_mini_player")
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0B0C16))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = 1.dp,
                                    color = Color(0xFF00F5FF).copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x11FFFFFF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AlbumArtThumbnail(
                                        track = track,
                                        fallbackIcon = Icons.Rounded.MusicNote,
                                        tint = Color(0xFF00F5FF),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = track.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        fontSize = 11.sp,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create New Playlist Dialog
    if (showCreateDialog) {
        CreatePlaylistDialog(
            viewModel = viewModel,
            onDismiss = { showCreateDialog = false }
        )
    }
}

// -----------------------------------------------------------------------------------------
// PLAYLIST OVERVIEW VIEW
// -----------------------------------------------------------------------------------------

@Composable
fun PlaylistOverviewView(
    playlists: List<PlaylistEntity>,
    viewModel: PlaybackViewModel,
    onOpenCustom: (PlaylistEntity) -> Unit,
    onOpenSmart: (SmartPlaylistType) -> Unit,
    onCreateClick: () -> Unit
) {
    val favStats by viewModel.favoriteStats.collectAsStateWithLifecycle()
    val favCount = remember(favStats) { favStats.count { it.isFavorite } }
    val allTracks = remember(viewModel.folders.collectAsStateWithLifecycle().value) { viewModel.getAllTracks() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section: SMART AUTO-GENERATED PLAYLISTS
        item {
            Text(
                text = "SMART PLAYLISTS",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                color = Color(0xFF00F5FF),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // Favorites card
        item {
            SmartPlaylistCard(
                title = "Favorites",
                subtitle = "$favCount favorite track(s)",
                accentColor = Color(0xFFFF2A6D),
                icon = Icons.Default.Favorite,
                onClick = { onOpenSmart(SmartPlaylistType.FAVORITES) },
                onPlay = {
                    val tracks = viewModel.getSmartPlaylistTracks(SmartPlaylistType.FAVORITES)
                    viewModel.playPlaylist(tracks)
                }
            )
        }

        // Recently Added card
        item {
            SmartPlaylistCard(
                title = "Recently Added",
                subtitle = "${minOf(50, allTracks.size)} recently added music tracks",
                accentColor = Color(0xFF00F5FF),
                icon = Icons.Rounded.Schedule,
                onClick = { onOpenSmart(SmartPlaylistType.RECENTLY_ADDED) },
                onPlay = {
                    val tracks = viewModel.getSmartPlaylistTracks(SmartPlaylistType.RECENTLY_ADDED)
                    viewModel.playPlaylist(tracks)
                }
            )
        }

        // Most Played card
        item {
            val mostPlayedStats by viewModel.mostPlayedStats.collectAsStateWithLifecycle()
            SmartPlaylistCard(
                title = "Most Played",
                subtitle = "${mostPlayedStats.size} track(s) in heavy rotation",
                accentColor = Color(0xFFFF9E00),
                icon = Icons.Rounded.Whatshot,
                onClick = { onOpenSmart(SmartPlaylistType.MOST_PLAYED) },
                onPlay = {
                    val tracks = viewModel.getSmartPlaylistTracks(SmartPlaylistType.MOST_PLAYED)
                    viewModel.playPlaylist(tracks)
                }
            )
        }

        // Section: CUSTOM PLAYLISTS
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CUSTOM PLAYLISTS (${playlists.size})",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                TextButton(
                    onClick = onCreateClick,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF00F5FF))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "NEW",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                        .clickable { onCreateClick() },
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0x1A00F5FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = null,
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Create Your First Playlist",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Organize your music files into custom collections with custom covers.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(playlists) { playlist ->
                CustomPlaylistRowItem(
                    playlist = playlist,
                    viewModel = viewModel,
                    onClick = { onOpenCustom(playlist) }
                )
            }
        }
    }
}

@Composable
fun SmartPlaylistCard(
    title: String,
    subtitle: String,
    accentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        borderWidth = 0.8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.15f))
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "SMART",
                                color = accentColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play $title",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CustomPlaylistRowItem(
    playlist: PlaylistEntity,
    viewModel: PlaybackViewModel,
    onClick: () -> Unit
) {
    val trackEntities by viewModel.getPlaylistTracksFlow(playlist.id).collectAsStateWithLifecycle(emptyList())

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        borderWidth = 0.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover Artwork
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1F00F5FF))
                        .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!playlist.customArtPath.isNullOrBlank() && File(playlist.customArtPath).exists()) {
                        AsyncImage(
                            model = File(playlist.customArtPath),
                            contentDescription = playlist.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color(0xFF00F5FF),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (playlist.description.isNotBlank()) playlist.description else "${trackEntities.size} track(s)",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Quick Play Button
            IconButton(
                onClick = {
                    val resolved = viewModel.resolveTracks(trackEntities)
                    viewModel.playPlaylist(resolved)
                },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x1A00F5FF))
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Playlist",
                    tint = Color(0xFF00F5FF),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// CUSTOM PLAYLIST DETAIL VIEW (With Full Reordering, Batch Delete, Swipe-To-Remove)
// -----------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPlaylistDetailView(
    playlist: PlaylistEntity,
    viewModel: PlaybackViewModel,
    onBackToOverview: () -> Unit
) {
    val context = LocalContext.current
    val trackEntities by viewModel.getPlaylistTracksFlow(playlist.id).collectAsStateWithLifecycle(emptyList())
    val resolvedTracks = remember(trackEntities, viewModel.folders.collectAsStateWithLifecycle().value) {
        viewModel.resolveTracks(trackEntities)
    }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    // Batch selection mode states
    var isBatchMode by remember { mutableStateOf(false) }
    val selectedTrackPaths = remember { mutableStateListOf<String>() }

    // Dialog states
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddTracksDialog by remember { mutableStateOf(false) }

    // Total Duration computation
    val totalDurationMs = remember(resolvedTracks) { resolvedTracks.sumOf { it.duration } }
    val durationFormatted = remember(totalDurationMs) {
        val totalSecs = totalDurationMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        String.format(Locale.getDefault(), "%d min %02d sec", mins, secs)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // PLAYLIST HEADER CARD
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            borderWidth = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x1F00F5FF))
                            .border(1.dp, Color(0xFF00F5FF), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!playlist.customArtPath.isNullOrBlank() && File(playlist.customArtPath).exists()) {
                            AsyncImage(
                                model = File(playlist.customArtPath),
                                contentDescription = playlist.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = null,
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (playlist.description.isNotBlank()) {
                            Text(
                                text = playlist.description,
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${resolvedTracks.size} tracks • $durationFormatted",
                            color = Color(0xFF00F5FF),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Edit playlist info
                    IconButton(
                        onClick = { showEditInfoDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Playlist",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete playlist
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete Playlist",
                            tint = Color(0xFFFF2A6D),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // PLAYLIST CONTROL BAR: Prominent 'Play All' and 'Shuffle All' Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play All
                    Button(
                        onClick = {
                            if (resolvedTracks.isNotEmpty()) {
                                viewModel.playPlaylist(resolvedTracks, startIndex = 0, shuffle = false)
                            }
                        },
                        enabled = resolvedTracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlist_play_all_button"),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "PLAY ALL",
                            color = Color.Black,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    // Shuffle All
                    OutlinedButton(
                        onClick = {
                            if (resolvedTracks.isNotEmpty()) {
                                viewModel.playPlaylist(resolvedTracks, shuffle = true)
                            }
                        },
                        enabled = resolvedTracks.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = Brush.horizontalGradient(listOf(Color(0xFF00F5FF), Color(0xFFBD00FF)))
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlist_shuffle_all_button"),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = null,
                            tint = Color(0xFF00F5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "SHUFFLE",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }

                    // Add Tracks Button
                    IconButton(
                        onClick = { showAddTracksDialog = true },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x1A00F5FF))
                            .testTag("playlist_add_tracks_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = "Add Tracks",
                            tint = Color(0xFF00F5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Batch Mode Toggle
                    IconButton(
                        onClick = {
                            isBatchMode = !isBatchMode
                            selectedTrackPaths.clear()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBatchMode) Color(0xFF00F5FF) else Color(0x1AFFFFFF))
                            .testTag("playlist_batch_mode_toggle")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = "Batch Select",
                            tint = if (isBatchMode) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // BATCH ACTIONS BAR (when batch mode active)
        AnimatedVisibility(visible = isBatchMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x2200F5FF))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${selectedTrackPaths.size} SELECTED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00F5FF),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (selectedTrackPaths.size == resolvedTracks.size) {
                                selectedTrackPaths.clear()
                            } else {
                                selectedTrackPaths.clear()
                                selectedTrackPaths.addAll(resolvedTracks.map { it.path })
                            }
                        }
                    ) {
                        Text(
                            if (selectedTrackPaths.size == resolvedTracks.size) "Deselect All" else "Select All",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }

                Button(
                    onClick = {
                        if (selectedTrackPaths.isNotEmpty()) {
                            viewModel.removeTracksFromPlaylist(playlist.id, selectedTrackPaths.toList())
                            Toast.makeText(context, "Removed ${selectedTrackPaths.size} track(s)", Toast.LENGTH_SHORT).show()
                            selectedTrackPaths.clear()
                            isBatchMode = false
                        }
                    },
                    enabled = selectedTrackPaths.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("REMOVE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // TRACK LIST
        if (resolvedTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Playlist is Empty",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Tap the '+ Add Tracks' button above to add music files from your device storage.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = resolvedTracks,
                    key = { _, track -> track.path }
                ) { index, track ->
                    val isCurrent = currentTrack?.path == track.path
                    val isSelectedInBatch = selectedTrackPaths.contains(track.path)

                    PlaylistTrackItemRow(
                        track = track,
                        index = index,
                        totalCount = resolvedTracks.size,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        isBatchMode = isBatchMode,
                        isSelectedInBatch = isSelectedInBatch,
                        onTrackClick = {
                            if (isBatchMode) {
                                if (isSelectedInBatch) selectedTrackPaths.remove(track.path)
                                else selectedTrackPaths.add(track.path)
                            } else {
                                viewModel.playPlaylist(resolvedTracks, startIndex = index, shuffle = false)
                            }
                        },
                        onMoveUp = {
                            if (index > 0) {
                                viewModel.moveTrackInPlaylist(playlist.id, index, index - 1)
                            }
                        },
                        onMoveDown = {
                            if (index < resolvedTracks.size - 1) {
                                viewModel.moveTrackInPlaylist(playlist.id, index, index + 1)
                            }
                        },
                        onRemove = {
                            viewModel.removeTrackFromPlaylist(playlist.id, track.path)
                            Toast.makeText(context, "Removed from playlist", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Dialogs
    if (showEditInfoDialog) {
        EditPlaylistInfoDialog(
            playlist = playlist,
            viewModel = viewModel,
            onDismiss = { showEditInfoDialog = false }
        )
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = {
                Text(
                    "DELETE PLAYLIST",
                    color = Color(0xFFFF2A6D),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text("Are you sure you want to delete '${playlist.title}'? Audio files on disk will not be deleted.", color = Color.White)
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deletePlaylist(playlist.id)
                        showDeleteConfirmDialog = false
                        onBackToOverview()
                        Toast.makeText(context, "Playlist deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D))
                ) {
                    Text("DELETE", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF0F101A)
        )
    }

    if (showAddTracksDialog) {
        AddTracksToPlaylistDialog(
            playlistId = playlist.id,
            alreadyAddedPaths = resolvedTracks.map { it.path }.toSet(),
            viewModel = viewModel,
            onDismiss = { showAddTracksDialog = false }
        )
    }
}

// -----------------------------------------------------------------------------------------
// SMART PLAYLIST DETAIL VIEW (Favorites, Recently Added, Most Played)
// -----------------------------------------------------------------------------------------

@Composable
fun SmartPlaylistDetailView(
    type: SmartPlaylistType,
    viewModel: PlaybackViewModel
) {
    val allTracks = remember(viewModel.folders.collectAsStateWithLifecycle().value) { viewModel.getAllTracks() }
    val favStats by viewModel.favoriteStats.collectAsStateWithLifecycle()
    val mostPlayedStats by viewModel.mostPlayedStats.collectAsStateWithLifecycle()

    val smartTracks = remember(type, allTracks, favStats, mostPlayedStats) {
        viewModel.getSmartPlaylistTracks(type)
    }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()

    val title = when (type) {
        SmartPlaylistType.FAVORITES -> "Favorites"
        SmartPlaylistType.RECENTLY_ADDED -> "Recently Added"
        SmartPlaylistType.MOST_PLAYED -> "Most Played"
    }

    val accentColor = when (type) {
        SmartPlaylistType.FAVORITES -> Color(0xFFFF2A6D)
        SmartPlaylistType.RECENTLY_ADDED -> Color(0xFF00F5FF)
        SmartPlaylistType.MOST_PLAYED -> Color(0xFFFF9E00)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // SMART PLAYLIST HEADER
        GlassBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            borderWidth = 1.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .border(1.dp, accentColor, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (type) {
                                SmartPlaylistType.FAVORITES -> Icons.Default.Favorite
                                SmartPlaylistType.RECENTLY_ADDED -> Icons.Rounded.Schedule
                                SmartPlaylistType.MOST_PLAYED -> Icons.Rounded.Whatshot
                            },
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = title,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Auto-generated smart collection • ${smartTracks.size} tracks",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Play All & Shuffle All Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (smartTracks.isNotEmpty()) {
                                viewModel.playPlaylist(smartTracks, startIndex = 0, shuffle = false)
                            }
                        },
                        enabled = smartTracks.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PLAY ALL", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (smartTracks.isNotEmpty()) {
                                viewModel.playPlaylist(smartTracks, shuffle = true)
                            }
                        },
                        enabled = smartTracks.isNotEmpty(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Shuffle, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHUFFLE", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }
            }
        }

        // TRACK LIST
        if (smartTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No tracks available", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(smartTracks) { index, track ->
                    val isCurrent = currentTrack?.path == track.path

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isCurrent) Color(0x2200F5FF) else Color(0x0AFFFFFF))
                            .border(1.dp, if (isCurrent) Color(0xFF00F5FF) else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                viewModel.playPlaylist(smartTracks, startIndex = index, shuffle = false)
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0x11FFFFFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                AlbumArtThumbnail(
                                    track = track,
                                    fallbackIcon = Icons.Rounded.MusicNote,
                                    tint = accentColor,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    color = if (isCurrent) accentColor else Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Format Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x1FADAEB5))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = track.format,
                                color = accentColor,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// TRACK ROW ITEM FOR CUSTOM PLAYLIST (Reordering & Delete)
// -----------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistTrackItemRow(
    track: AudioTrack,
    index: Int,
    totalCount: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isBatchMode: Boolean,
    isSelectedInBatch: Boolean,
    onTrackClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    // Swipe to dismiss state for quick removal
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else false
        }
    )

    val itemShape = RoundedCornerShape(14.dp)
    val borderWidth = if (isSelectedInBatch || isCurrent) 1.2.dp else 0.5.dp
    val borderColor = when {
        isSelectedInBatch -> Color(0xFF00F5FF)
        isCurrent -> Color(0xFF00F5FF).copy(alpha = 0.7f)
        else -> Color(0x1AFFFFFF)
    }
    val bgColor = when {
        isSelectedInBatch -> Color(0x2800F5FF)
        isCurrent -> Color(0x1600F5FF)
        else -> Color(0x0AFFFFFF)
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(itemShape)
                    .background(Color(0xFFFF2A6D))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(itemShape)
                .background(bgColor)
                .border(borderWidth, borderColor, itemShape)
                .clickable { onTrackClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isBatchMode) {
                    Checkbox(
                        checked = isSelectedInBatch,
                        onCheckedChange = { onTrackClick() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF00F5FF),
                            uncheckedColor = Color.Gray,
                            checkmarkColor = Color.Black
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                // Track art with glowing playing visualizer overlay
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x15FFFFFF))
                        .border(0.5.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArtThumbnail(
                        track = track,
                        fallbackIcon = Icons.Rounded.MusicNote,
                        tint = if (isCurrent) Color(0xFF00F5FF) else Color(0x88FFFFFF),
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            MiniVisualizer(isPlaying = isPlaying)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = if (isCurrent) Color(0xFF00F5FF) else Color.White,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (isCurrent && isPlaying) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF00F5FF))
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                        }
                        Text(
                            text = track.artist,
                            color = if (isCurrent) Color(0xFF00F5FF).copy(alpha = 0.85f) else Color(0xFF9E9EA6),
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (track.duration > 0) {
                            Text(
                                text = " • ${formatTime(track.duration)}",
                                color = Color(0xFF7A7A88),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // REORDER & ACTIONS CONTROLS
            if (!isBatchMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Move Up
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Move Up",
                            tint = if (index > 0) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Down
                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Move Down",
                            tint = if (index < totalCount - 1) Color.White.copy(alpha = 0.8f) else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete Track from Playlist
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove from Playlist",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------------------
// DIALOGS: CREATE, EDIT INFO, AND ADD TRACKS
// -----------------------------------------------------------------------------------------

@Composable
fun CreatePlaylistDialog(
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var coverPath by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val coversDir = File(context.filesDir, "playlist_covers")
                    if (!coversDir.exists()) coversDir.mkdirs()
                    val targetFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(targetFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    coverPath = targetFile.absolutePath
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "NEW PLAYLIST",
                color = Color(0xFF00F5FF),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color(0x44FFFFFF),
                        focusedLabelColor = Color(0xFF00F5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_playlist_title_field")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color(0x44FFFFFF),
                        focusedLabelColor = Color(0xFF00F5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (coverPath != null) {
                            AsyncImage(
                                model = File(coverPath!!),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF00F5FF))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (coverPath != null) "Cover Selected" else "Add Cover Image", color = Color.White, fontSize = 12.sp)
                        Text("Pick artwork from gallery", color = Color.Gray, fontSize = 10.sp)
                    }

                    if (coverPath != null) {
                        IconButton(onClick = { coverPath = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Red)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        Toast.makeText(context, "Please enter a title", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    viewModel.createPlaylist(title = title, description = description, customArtPath = coverPath) {
                        Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("CREATE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF0F101A)
    )
}

@Composable
fun EditPlaylistInfoDialog(
    playlist: PlaylistEntity,
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(playlist.title) }
    var description by remember { mutableStateOf(playlist.description) }
    var coverPath by remember { mutableStateOf(playlist.customArtPath) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val coversDir = File(context.filesDir, "playlist_covers")
                    if (!coversDir.exists()) coversDir.mkdirs()
                    val targetFile = File(coversDir, "cover_${System.currentTimeMillis()}.jpg")
                    val outputStream = FileOutputStream(targetFile)
                    inputStream.copyTo(outputStream)
                    inputStream.close()
                    outputStream.close()
                    coverPath = targetFile.absolutePath
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "EDIT PLAYLIST INFO",
                color = Color(0xFF00F5FF),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color(0x44FFFFFF),
                        focusedLabelColor = Color(0xFF00F5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color(0x44FFFFFF),
                        focusedLabelColor = Color(0xFF00F5FF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x33FFFFFF))
                            .clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (!coverPath.isNullOrBlank() && File(coverPath!!).exists()) {
                            AsyncImage(
                                model = File(coverPath!!),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = Color(0xFF00F5FF))
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (coverPath != null) "Change Artwork" else "Add Cover Artwork", color = Color.White, fontSize = 12.sp)
                        Text("Tap to select image", color = Color.Gray, fontSize = 10.sp)
                    }

                    if (coverPath != null) {
                        IconButton(onClick = { coverPath = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = null, tint = Color.Red)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        viewModel.updatePlaylistInfo(
                            playlist.copy(
                                title = title.trim(),
                                description = description.trim(),
                                customArtPath = coverPath
                            )
                        )
                        Toast.makeText(context, "Playlist updated", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("SAVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF0F101A)
    )
}

@Composable
fun AddTracksToPlaylistDialog(
    playlistId: Long,
    alreadyAddedPaths: Set<String>,
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allTracks = remember(viewModel.folders.collectAsStateWithLifecycle().value) { viewModel.getAllTracks() }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPaths = remember { mutableStateListOf<String>() }

    val filteredTracks = remember(searchQuery, allTracks, alreadyAddedPaths) {
        val available = allTracks.filter { !alreadyAddedPaths.contains(it.path) }
        if (searchQuery.isBlank()) available
        else available.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "ADD TRACKS TO PLAYLIST",
                color = Color(0xFF00F5FF),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search songs...", fontSize = 12.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF00F5FF), modifier = Modifier.size(18.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00F5FF),
                        unfocusedBorderColor = Color(0x33FFFFFF),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${selectedPaths.size} SELECTED (${filteredTracks.size} available)",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (filteredTracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (allTracks.all { alreadyAddedPaths.contains(it.path) }) "All library tracks are already in this playlist" else "No matching tracks found",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredTracks) { track ->
                            val isSelected = selectedPaths.contains(track.path)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0x2200F5FF) else Color(0x0AFFFFFF))
                                    .clickable {
                                        if (isSelected) selectedPaths.remove(track.path)
                                        else selectedPaths.add(track.path)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (isSelected) selectedPaths.remove(track.path)
                                        else selectedPaths.add(track.path)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF00F5FF),
                                        uncheckedColor = Color.Gray,
                                        checkmarkColor = Color.Black
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist,
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedPaths.isNotEmpty()) {
                        viewModel.addTracksToPlaylist(playlistId, selectedPaths.toList()) { count ->
                            Toast.makeText(context, "Added $count track(s) to playlist", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                },
                enabled = selectedPaths.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ADD (${selectedPaths.size})", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF0F101A)
    )
}
