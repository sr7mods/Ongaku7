package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
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
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.draw.blur
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.*
import com.example.data.AudioTrack
import com.example.data.FolderNode
import com.example.player.IconType
import com.example.player.PlaybackViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.GlassBox

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: PlaybackViewModel,
    onSettingsClick: () -> Unit,
    onMiniPlayerClick: () -> Unit,
    onPlaylistClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsState()

    // Continuous color shifting animation for premium border gradient
    val infiniteTransition = rememberInfiniteTransition(label = "border_glow")
    val animHue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "hue"
    )
    val borderBrush = remember(animHue) {
        val color1 = Color.hsv(animHue, 0.8f, 0.9f)
        val color2 = Color.hsv((animHue + 120f) % 360f, 0.8f, 0.9f)
        val color3 = Color.hsv((animHue + 240f) % 360f, 0.8f, 0.9f)
        Brush.linearGradient(listOf(color1, color2, color3, color1))
    }
    val currentFolder by viewModel.currentFolder.collectAsState()
    val folderNavigationStack by viewModel.folderNavigationStack.collectAsState()
    val expandedFolders by viewModel.expandedFolderPaths.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val iconType by viewModel.iconType.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    val selectedPaths by viewModel.selectedPaths.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var trackForPlaylist by remember { mutableStateOf<AudioTrack?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchRenameDialog by remember { mutableStateOf(false) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollToLastPlayed by viewModel.scrollToLastPlayed.collectAsState()

    // Intercept system back button to navigate up folders if nested
    androidx.activity.compose.BackHandler(enabled = currentFolder != null) {
        viewModel.navigateBackFolder()
    }

    // Setup permission launchers for storage
    val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permissionToRequest) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.scanAudio()
        }
    }

    // Auto-scan once permission is granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.scanAudio()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent, // Allows back glows to show
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedPaths.size} SELECTED",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F5FF),
                            fontSize = 16.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.exitSelectionMode() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Selection",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // SINGLE-FILE RENAME CONSTRAINT:
                        // 'Rename' must strictly work on a single file at a time. If multiple files are selected during long-press,
                        // hide/disable the 'Rename' action and allow only batch 'Delete'.
                        if (selectedPaths.size == 1) {
                            IconButton(
                                onClick = { showBatchRenameDialog = true },
                                modifier = Modifier.testTag("action_rename_selected")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Rename",
                                    tint = Color(0xFF00F5FF)
                                )
                            }
                        }
                        IconButton(
                            onClick = { showBatchDeleteDialog = true },
                            modifier = Modifier.testTag("action_delete_selected")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFFF2A6D)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F101A)
                    )
                )
            } else if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search music in storage...", color = Color.Gray, fontSize = 14.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00F5FF),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Close Search",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Search",
                                    tint = Color.Gray
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F101A)
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(32.dp)
                            ) {
                                // Subtle ambient glow behind the vector brand mark
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0x6600F5FF),
                                                    Color(0x22BD00FF),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                )
                                Image(
                                    painter = painterResource(id = com.example.R.drawable.ic_ongaku_vector_emblem),
                                    contentDescription = "Ongaku7 Brand Mark",
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "ONGAKU7",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 20.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                isSearchActive = true
                                searchQuery = ""
                            },
                            modifier = Modifier
                                .testTag("search_music_button")
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search Music from Storage",
                                tint = Color(0xFF00F5FF)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = onPlaylistClick,
                            modifier = Modifier
                                .testTag("main_playlists_nav_button")
                                .clip(CircleShape)
                                .background(Color(0x1A00F5FF))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Playlist Manager",
                                tint = Color(0xFF00F5FF)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.playLastPlayedTrack() },
                            modifier = Modifier
                                .testTag("play_last_played_button")
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play Last Played Music",
                                tint = Color(0xFF00F5FF)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier
                                .testTag("settings_gear_button")
                                .clip(CircleShape)
                                .background(Color(0x1AFFFFFF))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "System Settings",
                                tint = Color(0xFF00F5FF)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0x1F000000)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!hasPermission) {
                // Beautiful Permission Request Landing Box
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassBox(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Storage Access Required",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Ongaku7 needs permission to scan your internal and external storage folders recursively to index and organize all music formats.",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { permissionLauncher.launch(permissionToRequest) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00F5FF),
                                    contentColor = Color(0xFF070710)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "GRANT PERMISSION",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            } else if (isLoading) {
                // Loading scanning engine feedback
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFFBD00FF))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "SCANNING LOCAL FILES...",
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            } else if (folders.isEmpty()) {
                // Empty files state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    GlassBox(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFBD00FF),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No Audio Tracks Found",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "We scanned your directories but could not find supported formats (MP3, FLAC, WAV, M4A, OGG). Place audio files in your storage and retry.",
                                color = Color.LightGray,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(onClick = { viewModel.scanAudio(force = true) }) {
                                Text(
                                    "RESCAN STORAGE",
                                    color = Color(0xFF00F5FF),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Dynamic open-and-navigate folders/tracks view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    // Folder Breadcrumbs/Header with beautiful Material 3 Back Arrow
                    if (currentFolder != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.navigateBackFolder() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x15FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Go Back",
                                    tint = Color(0xFF00F5FF)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = currentFolder!!.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Navigate Up",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    val activeSubfolders = currentFolder?.subFolders ?: folders
                    val activeTracks = currentFolder?.tracks ?: emptyList()

                    // Auto-scroll to the currently playing/last played track if enabled
                    LaunchedEffect(currentFolder, currentTrack, scrollToLastPlayed) {
                        if (scrollToLastPlayed && currentTrack != null) {
                            val trackIndexInTracks = activeTracks.indexOfFirst { it.path == currentTrack?.path }
                            if (trackIndexInTracks != -1) {
                                val targetIndex = activeSubfolders.size + trackIndexInTracks
                                try {
                                    listState.animateScrollToItem(targetIndex)
                                } catch (e: Exception) {
                                    // ignore index shift errors
                                }
                            }
                        }
                    }

                    if (activeSubfolders.isEmpty() && activeTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "This folder is empty",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Sub-folders first
                            itemsIndexed(activeSubfolders, key = { _, sub -> "folder_${sub.path}" }) { index, sub ->
                                FolderRowItem(
                                    folder = sub,
                                    onFolderClick = { viewModel.openFolder(sub) },
                                    index = index
                                )
                            }

                            // Tracks next
                            itemsIndexed(activeTracks, key = { _, track -> "track_${track.path}" }) { index, track ->
                                val isSelected = selectedPaths.contains(track.path)
                                val isCurrentlyPlaying = currentTrack?.path == track.path
                                TrackRowItem(
                                    track = track,
                                    onTrackClick = { clickedTrack ->
                                        if (isSelectionMode) {
                                            viewModel.toggleSelection(clickedTrack.path)
                                        } else {
                                            viewModel.selectTrack(clickedTrack)
                                        }
                                    },
                                    isSelectionMode = isSelectionMode,
                                    isSelected = isSelected,
                                    onToggleSelect = {
                                        if (!isSelectionMode) {
                                            viewModel.enterSelectionMode(track.path)
                                        } else {
                                            viewModel.toggleSelection(track.path)
                                        }
                                    },
                                    isCurrent = isCurrentlyPlaying,
                                    isPlaying = isCurrentlyPlaying && isPlaying,
                                    onRename = { t, newName -> viewModel.renameTrackFile(t, newName) },
                                    onDelete = { t ->
                                        viewModel.enterSelectionMode(t.path)
                                        viewModel.performDeleteSelected(context)
                                    },
                                    onAddToPlaylist = { trackForPlaylist = it },
                                    index = index
                                )
                            }
                        }
                    }
                }
            }

            // Floating Mini-Player Bar at bottom
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
                            .testTag("floating_mini_player")
                    ) {
                        // Fully opaque deep premium obsidian backing layer to completely obscure things behind it
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0B0C16))
                        )

                        // Glowing animated color-changing gradient border and glassy sheen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(
                                    width = 1.5.dp,
                                    brush = borderBrush,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x19FFFFFF), // 10% white sheen
                                            Color(0x0A000000)  // Deep shadow depth
                                        )
                                    )
                                )
                        )

                        // Crystal clear layout content rendered perfectly on top of the blur
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
                                // Glowing micro music art badge
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
                                    val subtitleText = buildString {
                                        append(track.artist)
                                        if (track.album.isNotBlank() && track.album != "Unknown Album" && track.album != "<Unknown Album>") {
                                            append("  |  ")
                                            append(track.album)
                                        }
                                    }
                                    Text(
                                        text = subtitleText,
                                        fontSize = 11.sp,
                                        color = Color.LightGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Quick Play/Pause trigger
                            IconButton(
                                onClick = { viewModel.togglePlayPause() },
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("mini_player_play_pause")
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

            // Search Results Overlay
            if (isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xF5070710))
                        .zIndex(20f)
                ) {
                    val allTracks = remember(folders) { viewModel.getAllTracks() }
                    val filteredTracks = remember(searchQuery, allTracks) {
                        if (searchQuery.isBlank()) {
                            emptyList()
                        } else {
                            allTracks.filter { track ->
                                track.title.contains(searchQuery, ignoreCase = true) ||
                                track.artist.contains(searchQuery, ignoreCase = true) ||
                                track.album.contains(searchQuery, ignoreCase = true) ||
                                track.path.contains(searchQuery, ignoreCase = true)
                            }
                        }
                    }

                    if (searchQuery.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color(0xFF00F5FF),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "Type to Search Music",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Search through ${allTracks.size} indexed audio files across all folders in storage.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else if (filteredTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No tracks found matching '$searchQuery'",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            itemsIndexed(filteredTracks, key = { _, track -> "search_${track.path}" }) { index, track ->
                                val isCurrentlyPlaying = currentTrack?.path == track.path
                                TrackRowItem(
                                    track = track,
                                    onTrackClick = { clickedTrack ->
                                        viewModel.selectTrack(clickedTrack)
                                        isSearchActive = false
                                    },
                                    isSelectionMode = false,
                                    isSelected = false,
                                    onToggleSelect = {},
                                    isCurrent = isCurrentlyPlaying,
                                    isPlaying = isCurrentlyPlaying && isPlaying,
                                    onRename = { t, newName -> viewModel.renameTrackFile(t, newName) },
                                    onDelete = { t ->
                                        viewModel.enterSelectionMode(t.path)
                                        viewModel.performDeleteSelected(context)
                                    },
                                    onAddToPlaylist = { trackForPlaylist = it },
                                    index = index
                                )
                            }
                        }
                    }
                }
            }

            // Batch Delete Dialog
            if (showBatchDeleteDialog && selectedPaths.isNotEmpty()) {
                val count = selectedPaths.size
                AlertDialog(
                    onDismissRequest = { showBatchDeleteDialog = false },
                    title = {
                        Text(
                            text = if (count == 1) "DELETE FILE" else "DELETE $count FILES",
                            color = Color(0xFFFF2A6D),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    },
                    text = {
                        Text(
                            text = "Are you sure you want to permanently delete ${if (count == 1) "this file" else "these $count files"} from physical storage? This cannot be undone.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showBatchDeleteDialog = false
                                viewModel.performDeleteSelected(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D))
                        ) {
                            Text("DELETE", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchDeleteDialog = false }) {
                            Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    },
                    containerColor = Color(0xFF0F101A)
                )
            }

            // Batch Rename Dialog (Strict single-file constraint)
            if (showBatchRenameDialog && selectedPaths.size == 1) {
                val targetPath = selectedPaths.first()
                var renameInput by remember(targetPath) {
                    mutableStateOf(java.io.File(targetPath).nameWithoutExtension)
                }

                AlertDialog(
                    onDismissRequest = { showBatchRenameDialog = false },
                    title = {
                        Text(
                            "RENAME FILE",
                            color = Color(0xFF00F5FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    },
                    text = {
                        Column {
                            Text(
                                "Enter new file name:",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            OutlinedTextField(
                                value = renameInput,
                                onValueChange = { renameInput = it },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00F5FF),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val trimmed = renameInput.trim()
                                if (trimmed.isNotBlank()) {
                                    viewModel.performRenameSelected(trimmed)
                                }
                                showBatchRenameDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                            enabled = renameInput.isNotBlank()
                        ) {
                            Text("RENAME", color = Color.Black, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showBatchRenameDialog = false }) {
                            Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                        }
                    },
                    containerColor = Color(0xFF0F101A)
                )
            }

            // Add to Playlist Modal Bottom Sheet Dialog
            if (trackForPlaylist != null) {
                AddToPlaylistDialog(
                    track = trackForPlaylist!!,
                    viewModel = viewModel,
                    onDismiss = { trackForPlaylist = null }
                )
            }
        }
    }
}

/**
 * Single-level folder row for clean, open-and-navigate physical hierarchy view.
 */
@Composable
fun FolderRowItem(
    folder: FolderNode,
    onFolderClick: () -> Unit,
    index: Int = 0
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "folder_press_scale"
    )

    var isItemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(folder.path) {
        val delayMs = (index.coerceAtMost(8) * 35).toLong()
        kotlinx.coroutines.delay(delayMs)
        isItemVisible = true
    }

    val itemAlpha by animateFloatAsState(
        targetValue = if (isItemVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "folder_alpha"
    )
    val itemOffsetY by animateFloatAsState(
        targetValue = if (isItemVisible) 0f else 20f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "folder_offset_y"
    )

    GlassBox(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = itemAlpha
                translationY = itemOffsetY
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color(0xFF00F5FF))
            ) { onFolderClick() },
        shape = RoundedCornerShape(14.dp),
        borderWidth = 0.6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x1800F5FF))
                        .border(0.5.dp, Color(0x3300F5FF), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = "Folder",
                        tint = Color(0xFF00F5FF),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val totalTracks = folder.tracks.size
                    val totalSubfolders = folder.subFolders.size
                    val detailsText = when {
                        totalTracks > 0 && totalSubfolders > 0 -> "$totalTracks tracks • $totalSubfolders folders"
                        totalTracks > 0 -> "$totalTracks tracks"
                        totalSubfolders > 0 -> "$totalSubfolders folders"
                        else -> "empty"
                    }
                    Text(
                        text = detailsText,
                        color = Color(0xFF9E9EA6),
                        fontSize = 11.5.sp
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Open Folder",
                tint = Color(0xFF70707D),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun MiniVisualizer(isPlaying: Boolean, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
    
    val height1 = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        ).value
    } else 0.2f

    val height2 = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.4f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        ).value
    } else 0.2f

    val height3 = if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        ).value
    } else 0.2f

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(height1, height2, height3).forEach { heightPercent ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(heightPercent)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color(0xFF00F5FF))
            )
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            color = Color(0xFF00F5FF),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRowItem(
    track: AudioTrack,
    onTrackClick: (AudioTrack) -> Unit,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onRename: (AudioTrack, String) -> Unit = { _, _ -> },
    onDelete: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (AudioTrack) -> Unit = {},
    index: Int = 0,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "track_press_scale"
    )

    var isItemVisible by remember { mutableStateOf(false) }
    LaunchedEffect(track.path) {
        val delayMs = (index.coerceAtMost(10) * 35).toLong()
        kotlinx.coroutines.delay(delayMs)
        isItemVisible = true
    }

    val itemAlpha by animateFloatAsState(
        targetValue = if (isItemVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "track_alpha"
    )
    val itemOffsetY by animateFloatAsState(
        targetValue = if (isItemVisible) 0f else 20f,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "track_offset_y"
    )

    val itemShape = RoundedCornerShape(14.dp)
    val borderWidth = if (isSelected || isCurrent) 1.2.dp else 0.5.dp
    val borderColor = when {
        isSelected -> Color(0xFF00F5FF)
        isCurrent -> Color(0xFF00F5FF).copy(alpha = 0.7f)
        else -> Color(0x1AFFFFFF)
    }
    val bgColor = when {
        isSelected -> Color(0x2800F5FF)
        isCurrent -> Color(0x1600F5FF)
        else -> Color(0x0AFFFFFF)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = itemAlpha
                translationY = itemOffsetY
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(itemShape)
            .background(bgColor)
            .border(borderWidth, borderColor, itemShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color(0xFF00F5FF)),
                onClick = { onTrackClick(track) },
                onLongClick = { onToggleSelect() }
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    
                    // Show animated visualizer overlay on playing track thumbnail
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Selected",
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier.size(15.dp).padding(end = 4.dp)
                            )
                        }
                        Text(
                            text = track.title,
                            color = if (isSelected || isCurrent) Color(0xFF00F5FF) else Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

            // Move music file type format badge and options icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x1800F5FF))
                        .border(0.5.dp, Color(0x3300F5FF), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = track.format,
                        color = Color(0xFF00F5FF),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Three-dot options menu
                var showMenu by remember { mutableStateOf(false) }
                var showInfoDialog by remember { mutableStateOf(false) }
                var showRenameDialog by remember { mutableStateOf(false) }
                var showDeleteDialog by remember { mutableStateOf(false) }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Track Options",
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF0F101A))
                            .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play Now", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showMenu = false
                                onTrackClick(track)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(if (isSelected) "Deselect" else "Select", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showMenu = false
                                onToggleSelect()
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlaylistAdd,
                                        contentDescription = null,
                                        tint = Color(0xFF00F5FF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add to Playlist", color = Color(0xFF00F5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist(track)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF2A6D), fontSize = 12.sp) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("File Info", color = Color.White, fontSize = 12.sp) },
                            onClick = {
                                showMenu = false
                                showInfoDialog = true
                            }
                        )
                    }
                }

                if (showInfoDialog) {
                    AlertDialog(
                        onDismissRequest = { showInfoDialog = false },
                        title = {
                            Text(
                                "TRACK INFO",
                                color = Color(0xFF00F5FF),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                InfoRow("Title", track.title)
                                InfoRow("Artist", track.artist)
                                InfoRow("Album", track.album)
                                InfoRow("Format", track.format)
                                InfoRow("Duration", formatTime(track.duration))
                                InfoRow("Size", formatSize(track.size))
                                InfoRow("Path", track.path)
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showInfoDialog = false }) {
                                Text("CLOSE", color = Color(0xFF00F5FF), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF0F101A),
                        titleContentColor = Color.White,
                        textContentColor = Color.LightGray
                    )
                }

                if (showRenameDialog) {
                    var tempFileName by remember { mutableStateOf(java.io.File(track.path).nameWithoutExtension) }

                    AlertDialog(
                        onDismissRequest = { showRenameDialog = false },
                        title = {
                            Text(
                                "RENAME TRACK",
                                color = Color(0xFF00F5FF),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        text = {
                            OutlinedTextField(
                                value = tempFileName,
                                onValueChange = { tempFileName = it },
                                label = { Text("New File Name", color = Color.Gray) },
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
                            TextButton(
                                onClick = {
                                    onRename(track, tempFileName)
                                    showRenameDialog = false
                                }
                            ) {
                                Text("RENAME", color = Color(0xFF00F5FF), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showRenameDialog = false }) {
                                Text("CANCEL", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        },
                        containerColor = Color(0xFF0F101A),
                        titleContentColor = Color.White,
                        textContentColor = Color.LightGray
                    )
                }

                if (showDeleteDialog) {
                    AlertDialog(
                        onDismissRequest = { showDeleteDialog = false },
                        title = {
                            Text(
                                "DELETE FILE",
                                color = Color(0xFFFF2A6D),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        },
                        text = {
                            Text(
                                "Permanently delete \"${track.title}\" from storage? This cannot be undone.",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                    onDelete(track)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2A6D))
                            ) {
                                Text("DELETE", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteDialog = false }) {
                                Text("CANCEL", color = Color.Gray, fontFamily = FontFamily.Monospace)
                            }
                        },
                        containerColor = Color(0xFF0F101A)
                    )
                }
            }
        }
    }
}


object AlbumArtCache {
    private val cache = android.util.LruCache<String, android.graphics.Bitmap>(50)

    fun get(path: String): android.graphics.Bitmap? = cache.get(path)
    fun put(path: String, bitmap: android.graphics.Bitmap) {
        cache.put(path, bitmap)
    }
}

@Composable
fun AlbumArtThumbnail(
    track: AudioTrack,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = Icons.Rounded.MusicNote,
    tint: Color = Color.White
) {
    val key = track.customArtPath ?: track.path
    var bitmap by remember(key) {
        mutableStateOf(AlbumArtCache.get(key))
    }

    if (bitmap == null) {
        LaunchedEffect(key) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val cached = AlbumArtCache.get(key)
                    if (cached != null) {
                        cached
                    } else {
                        val decoded = if (!track.customArtPath.isNullOrBlank()) {
                            val customFile = java.io.File(track.customArtPath)
                            if (customFile.exists()) {
                                android.graphics.BitmapFactory.decodeFile(track.customArtPath)
                            } else null
                        } else {
                            val file = java.io.File(track.path)
                            if (file.exists()) {
                                val retriever = android.media.MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(track.path)
                                    val artBytes = retriever.embeddedPicture
                                    if (artBytes != null) {
                                        val options = android.graphics.BitmapFactory.Options().apply {
                                            inSampleSize = 4 // Decode at smaller size for thumbnails!
                                        }
                                        android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size, options)
                                    } else {
                                        val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                                        if (externalArtPath != null) {
                                            android.graphics.BitmapFactory.decodeFile(externalArtPath)
                                        } else null
                                    }
                                } catch (t: Throwable) {
                                    val externalArtPath = com.example.data.MediaExtraHelper.findExternalAlbumArt(track.path)
                                    if (externalArtPath != null) {
                                        android.graphics.BitmapFactory.decodeFile(externalArtPath)
                                    } else null
                                } finally {
                                    try {
                                        retriever.release()
                                    } catch (t: Throwable) {}
                                }
                            } else null
                        }
                        if (decoded != null) {
                            AlbumArtCache.put(key, decoded)
                        }
                        decoded
                    }
                } catch (e: Exception) {
                    null
                }
            }
            bitmap = loaded
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "Album Art",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        Icon(
            imageVector = fallbackIcon,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
    }
}



