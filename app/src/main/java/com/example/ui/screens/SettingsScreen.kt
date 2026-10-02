package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Facebook
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.player.AlbumArtType
import com.example.player.AppIconManager
import com.example.player.IconType
import com.example.player.PlaybackViewModel
import com.example.player.SortOrder
import com.example.player.SortType
import com.example.ui.components.DropdownOption
import com.example.ui.components.GlassBox
import com.example.ui.components.PremiumDropdownCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val iconType by viewModel.iconType.collectAsState()
    val albumArtType by viewModel.albumArtType.collectAsState()
    val sortType by viewModel.sortType.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val showHiddenFiles by viewModel.showHiddenFiles.collectAsState()
    val scrollToLastPlayed by viewModel.scrollToLastPlayed.collectAsState()
    val activeAppIconId by viewModel.activeAppIcon.collectAsState()

    var isOperatingBackup by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupStatusIsError by remember { mutableStateOf(false) }

    // SAF Create Document launcher for JSON Export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isOperatingBackup = true
                val res = viewModel.exportSettingsBackup(uri, context.contentResolver)
                isOperatingBackup = false
                res.fold(
                    onSuccess = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        backupStatusMessage = msg
                        backupStatusIsError = false
                    },
                    onFailure = { err ->
                        val errText = "Export Failed: ${err.message ?: "Unknown error"}"
                        Toast.makeText(context, errText, Toast.LENGTH_LONG).show()
                        backupStatusMessage = errText
                        backupStatusIsError = true
                    }
                )
            }
        }
    }

    // SAF Open Document launcher for JSON Import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isOperatingBackup = true
                val res = viewModel.importSettingsBackup(uri, context.contentResolver)
                isOperatingBackup = false
                res.fold(
                    onSuccess = { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        backupStatusMessage = msg
                        backupStatusIsError = false
                    },
                    onFailure = { err ->
                        val errText = "Import Failed: ${err.message ?: "Invalid JSON format"}"
                        Toast.makeText(context, errText, Toast.LENGTH_LONG).show()
                        backupStatusMessage = errText
                        backupStatusIsError = true
                    }
                )
            }
        }
    }

    // Helper to open social/URL links safely
    val openUrl = { url: String ->
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ONGAKU7 SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        letterSpacing = 1.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x33000000)
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // ==========================================
            // 1. TOP SECTION (PINNED): Rescan Media Library
            // ==========================================
            item {
                SectionHeader(
                    title = "STORAGE MANAGEMENT",
                    subtitle = "Pinned quick storage sync & indexer",
                    icon = Icons.Default.Storage,
                    accentColor = Color(0xFF00F5FF)
                )
            }

            item {
                var isScanning by remember { mutableStateOf(false) }
                val rotationAngle by animateFloatAsState(
                    targetValue = if (isScanning) 360f * 3 else 0f,
                    animationSpec = tween(
                        durationMillis = 1200,
                        easing = LinearOutSlowInEasing
                    ),
                    finishedListener = { isScanning = false },
                    label = "RescanRotation"
                )

                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF00F5FF).copy(alpha = 0.5f), Color(0xFFBD00FF).copy(alpha = 0.2f))
                            ),
                            RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Rescan Media Library",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Deep scan device storage to index audio tracks, embedded lyrics, and folder structures.",
                                color = Color.LightGray.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        IconButton(
                            onClick = {
                                isScanning = true
                                viewModel.scanAudio(force = true)
                                Toast.makeText(context, "Scanning media storage...", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x2200F5FF))
                                .border(1.5.dp, Color(0xFF00F5FF), CircleShape)
                                .testTag("rescan_storages_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan Storages",
                                tint = Color(0xFF00F5FF),
                                modifier = Modifier
                                    .size(24.dp)
                                    .rotate(rotationAngle)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. APPEARANCE SECTION: App Icon & UI Vectors
            // ==========================================
            item {
                SectionHeader(
                    title = "APPEARANCE & THEME",
                    subtitle = "Personalize visual aesthetics, icons, and artwork presentation",
                    icon = Icons.Default.Palette,
                    accentColor = Color(0xFFBD00FF)
                )
            }

            // Dynamic App Icon Switcher Card
            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFFBD00FF).copy(alpha = 0.4f), Color(0xFF00F5FF).copy(alpha = 0.3f))
                            ),
                            RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "DYNAMIC APP ICON SELECTOR",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00F5FF),
                            fontSize = 13.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Switch launcher home screen icon dynamically via Android activity aliases.",
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppIconManager.ICON_OPTIONS.forEach { option ->
                                val isSelected = activeAppIconId == option.id

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val changed = viewModel.setAppIcon(option.id)
                                            if (changed) {
                                                Toast.makeText(
                                                    context,
                                                    "Launcher icon switched to: ${option.title}",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .testTag("app_icon_${option.id}"),
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) Color(0x3300F5FF) else Color(0x11FFFFFF),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF00F5FF) else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Icon preview card
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0x33000000))
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Color(0xFF00F5FF) else Color.White.copy(alpha = 0.2f),
                                                    RoundedCornerShape(12.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Image(
                                                painter = painterResource(id = option.previewRes),
                                                contentDescription = option.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = option.title,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSelected) Color(0xFF00F5FF) else Color.White,
                                                    fontSize = 14.sp
                                                )
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(6.dp),
                                                        color = Color(0xFF00F5FF).copy(alpha = 0.2f),
                                                        border = BorderStroke(1.dp, Color(0xFF00F5FF))
                                                    ) {
                                                        Text(
                                                            text = "ACTIVE",
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = Color(0xFF00F5FF),
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = option.subtitle,
                                                color = Color.LightGray.copy(alpha = 0.7f),
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }

                                        // Selection Radio with checkmark
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(if (isSelected) Color(0xFF00F5FF) else Color.Transparent)
                                                .border(
                                                    1.5.dp,
                                                    if (isSelected) Color(0xFF00F5FF) else Color.Gray.copy(alpha = 0.5f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF0B0C16),
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom Album Art Dropdown
            item {
                PremiumDropdownCard(
                    title = "ALBUM ART VISUAL STYLE",
                    subtitle = "Sleek rendering presentation for cover artwork in the player",
                    selectedOption = albumArtType,
                    options = listOf(
                        DropdownOption(AlbumArtType.ROUNDED, "Rounded Minimal", "Modern subtle rounded corners with high-contrast frame", "DEFAULT"),
                        DropdownOption(AlbumArtType.ROTATING_VINYL, "Cyberpunk Vinyl", "Authentic rotating turntable vinyl with inertia dynamics", "ANIMATED"),
                        DropdownOption(AlbumArtType.FLOATING_CARD, "Floating 3D Card", "Elevated card with frosted drop shadow and floating physics", "3D GLASS"),
                        DropdownOption(AlbumArtType.AMBIENT_GLOW, "Ambient Glow Aura", "Dynamic reactive canvas blur aura pulsing behind cover art", "REACTIVE")
                    ),
                    onOptionSelected = { viewModel.setAlbumArtType(it) },
                    accentColor = Color(0xFF00F5FF)
                )
            }

            // Custom Icon Style Dropdown
            item {
                PremiumDropdownCard(
                    title = "SYSTEM ICON STYLE",
                    subtitle = "Personalize playback and navigation vector aesthetics",
                    selectedOption = iconType,
                    options = listOf(
                        DropdownOption(IconType.CYBERPUNK, "Cyberpunk Neon", "High-contrast glowing cyan & purple neon vectors", "NEON"),
                        DropdownOption(IconType.FILLED, "Material Filled", "Solid high-visibility Material 3 filled symbols", "CLEAN"),
                        DropdownOption(IconType.OUTLINED, "Minimal Outlined", "Sleek modern linear stroke outlines", "MINIMAL"),
                        DropdownOption(IconType.ROUNDED, "Soft Rounded", "Friendly softened corner icon contours", "SOFT")
                    ),
                    onOptionSelected = { viewModel.setIconType(it) },
                    accentColor = Color(0xFFBD00FF)
                )
            }

            // ==========================================
            // 3. GENERAL SECTION: File System & Sort
            // ==========================================
            item {
                SectionHeader(
                    title = "GENERAL PREFERENCES",
                    subtitle = "File scanning, playlist behavior, and track ordering",
                    icon = Icons.Default.Settings,
                    accentColor = Color(0xFF00E676)
                )
            }

            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFF00E676).copy(alpha = 0.4f), Color(0xFF00F5FF).copy(alpha = 0.2f))
                            ),
                            RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Show Hidden Audio Files",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Ignore .nomedia files to display audio located in hidden system folders.",
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = showHiddenFiles,
                                onCheckedChange = { viewModel.setShowHiddenFiles(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00F5FF),
                                    checkedTrackColor = Color(0xFF00F5FF).copy(alpha = 0.5f)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Divider(color = Color.White.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scroll to Last Played Media",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Automatically center the last played audio file in folder view when opened.",
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = scrollToLastPlayed,
                                onCheckedChange = { viewModel.setScrollToLastPlayed(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF00F5FF),
                                    checkedTrackColor = Color(0xFF00F5FF).copy(alpha = 0.5f)
                                )
                            )
                        }
                    }
                }
            }

            // Music Sort Criteria & Direction
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumDropdownCard(
                        title = "SORT CRITERIA",
                        subtitle = "Select sorting criteria for audio tracks and folders",
                        selectedOption = sortType,
                        options = listOf(
                            DropdownOption(SortType.TIME, "Date Modified / Time", "Newest audio files appear first", "RECENTS"),
                            DropdownOption(SortType.TYPE, "Audio Format / Type", "Group by FLAC, MP3, WAV, AAC formats", "CODEC"),
                            DropdownOption(SortType.ALPHABET, "Alphabetical (A - Z)", "Ordered alphabetically by title or filename", "TITLES")
                        ),
                        onOptionSelected = { viewModel.setSortType(it) },
                        accentColor = Color(0xFF00F5FF)
                    )

                    PremiumDropdownCard(
                        title = "SORT DIRECTION",
                        subtitle = "Ascending or descending direction for tracks",
                        selectedOption = sortOrder,
                        options = listOf(
                            DropdownOption(SortOrder.ASCENDING, "Ascending Order", "Low to high (A-Z, oldest to newest)", "NORMAL"),
                            DropdownOption(SortOrder.DESCENDING, "Descending Order", "High to low (Z-A, newest to oldest)", "REVERSE")
                        ),
                        onOptionSelected = { viewModel.setSortOrder(it) },
                        accentColor = Color(0xFF00F5FF)
                    )
                }
            }

            // ==========================================
            // 4. RESTORE AND BACKUP SECTION
            // ==========================================
            item {
                SectionHeader(
                    title = "RESTORE AND BACKUP",
                    subtitle = "Export or restore custom playlists, presets, and preferences",
                    icon = Icons.Rounded.SettingsBackupRestore,
                    accentColor = Color(0xFFFF9100)
                )
            }

            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                listOf(Color(0xFFFF9100).copy(alpha = 0.4f), Color(0xFF00F5FF).copy(alpha = 0.3f))
                            ),
                            RoundedCornerShape(20.dp)
                        ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "JSON Configuration & Playlist Backup",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Export playlists, favorites, 8D audio presets, equalizer curves, and UI configuration into a portable JSON backup file, or restore instantly via SAF.",
                            color = Color.LightGray.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )

                        if (backupStatusMessage != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (backupStatusIsError) Color(0x33FF1744) else Color(0x2200F5FF)
                                    )
                                    .border(
                                        1.dp,
                                        if (backupStatusIsError) Color(0xFFFF1744) else Color(0xFF00F5FF),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = backupStatusMessage ?: "",
                                    color = if (backupStatusIsError) Color(0xFFFF8A80) else Color(0xFF80D8FF),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Export Button
                            Button(
                                onClick = {
                                    val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
                                    val filename = "Ongaku7_Settings_Backup_${sdf.format(Date())}.json"
                                    exportLauncher.launch(filename)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("export_settings_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00F5FF),
                                    contentColor = Color(0xFF0B0C16)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isOperatingBackup
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileUpload,
                                    contentDescription = "Export Settings",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Export JSON",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }

                            // Import Button
                            OutlinedButton(
                                onClick = {
                                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("import_settings_button"),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF00F5FF)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF00F5FF)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isOperatingBackup
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FileDownload,
                                    contentDescription = "Import Settings",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Import JSON",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // 5. BOTTOM SECTION: About Developer & Designers
            // ==========================================
            item {
                SectionHeader(
                    title = "ABOUT DEVELOPER & DESIGNERS",
                    subtitle = "Engineered with passion by SR7 Mods & SR7 Crasher",
                    icon = Icons.Default.Info,
                    accentColor = Color(0xFFFF4081)
                )
            }

            item {
                GlassBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            2.dp,
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFBD00FF), Color(0xFF00F5FF))
                            ),
                            RoundedCornerShape(24.dp)
                        ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // SR7 Styled Avatar badge with glowing holographic ring
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        colors = listOf(
                                            Color(0xFF00F5FF),
                                            Color(0xFFBD00FF),
                                            Color(0xFFFF007F),
                                            Color(0xFF00F5FF)
                                        )
                                    )
                                )
                                .padding(3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_sr7_dev_icon_1789367390119),
                                contentDescription = "SR7 Developer Icon",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "SR7 Mods",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33BD00FF),
                            border = BorderStroke(1.dp, Color(0xFFBD00FF).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Ongaku 7 v2.4.0 • Studio Edition",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F5FF),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            "Expertise in Android Reverse Engineering, Custom System Modification, and High-Performance Audio Processing. Icon Design collaboration with SR7 Crasher.",
                            color = Color.LightGray.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            "CONNECT CHANNELS",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        // Social channels row 1
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SocialButton(
                                icon = Icons.Outlined.Storefront,
                                contentDesc = "Mod Store",
                                color = Color(0xFF00F5FF),
                                onClick = { openUrl("https://alexsifatrayhan.github.io/sr7mods-store/") }
                            )
                            SocialButton(
                                icon = Icons.Outlined.Code,
                                contentDesc = "GitHub",
                                color = Color.White,
                                onClick = { openUrl("https://github.com/alexsifatrayhan") }
                            )
                            SocialButton(
                                icon = Icons.Outlined.Facebook,
                                contentDesc = "Facebook",
                                color = Color(0xFF1877F2),
                                onClick = { openUrl("https://m.facebook.com/sifatrayhan2007") }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Social channels row 2
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            SocialButton(
                                icon = Icons.Default.Send,
                                contentDesc = "Telegram",
                                color = Color(0xFF24A1DE),
                                onClick = { openUrl("https://t.me/sr7mods") }
                            )
                            SocialButton(
                                icon = Icons.Default.Phone,
                                contentDesc = "WhatsApp",
                                color = Color(0xFF25D366),
                                onClick = { openUrl("https://wa.me/+8801318930997") }
                            )
                            SocialButton(
                                icon = Icons.Outlined.AlternateEmail,
                                contentDesc = "Email",
                                color = Color(0xFFEA4335),
                                onClick = { openUrl("mailto:mrsrff12@gmail.com") }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp
            )
        }
        Text(
            text = subtitle,
            color = Color.LightGray.copy(alpha = 0.65f),
            fontSize = 11.sp
        )
    }
}

@Composable
fun SocialButton(
    icon: ImageVector,
    contentDesc: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0x15FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
    }
}
