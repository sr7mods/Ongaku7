package com.example.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.AudioTrack
import com.example.data.playlist.PlaylistEntity
import com.example.player.PlaybackViewModel
import com.example.ui.screens.AlbumArtThumbnail
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    track: AudioTrack,
    viewModel: PlaybackViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    var isCreatingNew by remember { mutableStateOf(false) }

    // New playlist state
    var newTitle by remember { mutableStateOf("") }
    var newDescription by remember { mutableStateOf("") }
    var newCoverPath by remember { mutableStateOf<String?>(null) }

    // Zero-permission Android Photo Picker for playlist cover image
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
                    newCoverPath = targetFile.absolutePath
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F101A),
        contentColor = Color.White,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF00F5FF).copy(alpha = 0.5f))
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header: Target Track Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x1A00F5FF))
                    .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x22FFFFFF)),
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ADD TO PLAYLIST",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = Color(0xFF00F5FF),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = track.title,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Create New Playlist toggle / form
            if (!isCreatingNew) {
                OutlinedButton(
                    onClick = { isCreatingNew = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_new_playlist_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF00F5FF)
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF00F5FF), Color(0xFFBD00FF)))
                    )
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "CREATE NEW PLAYLIST",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                // New Playlist Creation Form
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x22FFFFFF)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF00F5FF).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(bottom = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "NEW PLAYLIST",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00F5FF),
                                fontSize = 14.sp
                            )
                            IconButton(
                                onClick = { isCreatingNew = false },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.Gray)
                            }
                        }

                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            label = { Text("Playlist Title *", fontSize = 12.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00F5FF),
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedLabelColor = Color(0xFF00F5FF),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("new_playlist_title_input")
                        )

                        OutlinedTextField(
                            value = newDescription,
                            onValueChange = { newDescription = it },
                            label = { Text("Description (Optional)", fontSize = 12.sp) },
                            maxLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00F5FF),
                                unfocusedBorderColor = Color(0x44FFFFFF),
                                focusedLabelColor = Color(0xFF00F5FF),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Cover Image Selection
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                if (newCoverPath != null) {
                                    AsyncImage(
                                        model = File(newCoverPath!!),
                                        contentDescription = "Cover Image",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Pick Cover",
                                        tint = Color(0xFF00F5FF),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (newCoverPath != null) "Cover Image Selected" else "Add Cover Artwork",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Optional custom image for your playlist",
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }

                            if (newCoverPath != null) {
                                IconButton(
                                    onClick = { newCoverPath = null },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear Cover", tint = Color.Red)
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (newTitle.isBlank()) {
                                    Toast.makeText(context, "Please enter a playlist title", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.createPlaylist(
                                    title = newTitle,
                                    description = newDescription,
                                    customArtPath = newCoverPath,
                                    initialTrackPath = track.path
                                ) {
                                    Toast.makeText(context, "Created '${newTitle}' and added track", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            enabled = newTitle.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_and_add_playlist_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F5FF)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "CREATE & ADD TRACK",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Existing Playlists Section
            Text(
                text = "EXISTING PLAYLISTS (${playlists.size})",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.LightGray,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "No playlists yet",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistSelectItem(
                            playlist = playlist,
                            onSelect = {
                                viewModel.addTrackToPlaylist(playlist.id, track.path) { success ->
                                    if (success) {
                                        Toast.makeText(context, "Added to '${playlist.title}'", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Already in '${playlist.title}'", Toast.LENGTH_SHORT).show()
                                    }
                                    onDismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectItem(
    playlist: PlaylistEntity,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x11FFFFFF))
            .clickable { onSelect() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    .background(Color(0x2200F5FF)),
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
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.AddCircleOutline,
            contentDescription = "Add Track to Playlist",
            tint = Color(0xFF00F5FF),
            modifier = Modifier.size(22.dp)
        )
    }
}
