package com.example.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import android.util.Log
import java.io.File

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val duration: Long,
    val size: Long,
    val format: String,
    val genre: String = "Unknown",
    val year: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val comment: String = "",
    val customArtPath: String? = null
)

data class FolderNode(
    val name: String,
    val path: String,
    val subFolders: MutableList<FolderNode> = mutableListOf(),
    val tracks: MutableList<AudioTrack> = mutableListOf()
)

object MediaExtraHelper {

    /**
     * Search for external album art image in the same directory as audio file.
     * Looks for exact base name matches (e.g. heat wave.png / jpg / webp)
     * as well as standard cover/folder/album images in the same folder.
     */
    fun findExternalAlbumArt(audioPath: String): String? {
        try {
            val audioFile = File(audioPath)
            val parent = audioFile.parentFile ?: return null
            if (!parent.exists() || !parent.isDirectory) return null

            val baseName = audioFile.nameWithoutExtension.lowercase()
            val imageExtensions = setOf("png", "jpg", "jpeg", "webp")

            val parentFiles = parent.listFiles() ?: emptyArray()

            // 1. Exact base name match with image extension
            for (file in parentFiles) {
                if (file.isFile) {
                    val fileBase = file.nameWithoutExtension.lowercase()
                    val fileExt = file.extension.lowercase()
                    if (fileBase == baseName && fileExt in imageExtensions) {
                        return file.absolutePath
                    }
                }
            }

            // 2. Generic cover/folder/album/artwork in the same directory
            val genericNames = setOf("cover", "folder", "album", "artwork", "front")
            for (file in parentFiles) {
                if (file.isFile) {
                    val fileBase = file.nameWithoutExtension.lowercase()
                    val fileExt = file.extension.lowercase()
                    if (fileBase in genericNames && fileExt in imageExtensions) {
                        return file.absolutePath
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore file read exceptions
        }
        return null
    }

    /**
     * Retrieves internal or external lyrics (.lrc, .srt, .txt, or embedded ID3 tags).
     */
    fun getLyrics(audioPath: String): String? {
        val parsed = com.example.player.lyrics.LyricsParser.loadLyricsForAudio(audioPath)
        return parsed?.rawText
    }

    /**
     * Retrieves parsed time-synced lyrics result model.
     */
    fun getParsedLyrics(audioPath: String): com.example.player.lyrics.LyricsResult? {
        return com.example.player.lyrics.LyricsParser.loadLyricsForAudio(audioPath)
    }
}

object AudioStorageScanner {

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "aac", "flac", "wav", "m4a", "ogg", "opus", "amr"
    )

    private fun isHiddenOrNoMedia(file: File): Boolean {
        var current: File? = file
        while (current != null && current.absolutePath != "/") {
            if (current.name.startsWith(".") && current.name != ".") return true
            val nomedia = File(current, ".nomedia")
            if (nomedia.exists()) return true
            current = current.parentFile
        }
        return false
    }

    fun scanAudioFiles(context: Context, showHiddenFiles: Boolean): List<FolderNode> {
        val rawTracks = mutableListOf<AudioTrack>()
        val contentResolver = context.contentResolver

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE
        )

        // Query both external and internal media store
        val urisToQuery = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.INTERNAL_CONTENT_URI
        )

        for (mediaUri in urisToQuery) {
            try {
                contentResolver.query(
                    mediaUri,
                    projection,
                    "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                    null,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                    while (cursor.moveToNext()) {
                        val originalPath = cursor.getString(dataCol) ?: continue
                        val rawFile = File(originalPath)
                        if (!rawFile.exists() || !rawFile.isFile || rawFile.length() <= 0L) {
                            continue
                        }

                        // Canonical path normalization: Resolves /sdcard -> /storage/emulated/0, symlinks, and aliases
                        val canonicalFile = try { rawFile.canonicalFile } catch (e: Exception) { rawFile }
                        val canonicalPath = canonicalFile.path

                        // Strict storage boundary check
                        val isStoragePath = canonicalPath.startsWith("/storage/") ||
                                canonicalPath.startsWith("/sdcard") ||
                                canonicalPath.startsWith("/mnt/")
                        if (!isStoragePath) continue

                        val ext = canonicalFile.extension.lowercase()
                        if (ext !in SUPPORTED_EXTENSIONS) continue

                        // Check if hidden or .nomedia
                        if (!showHiddenFiles && isHiddenOrNoMedia(canonicalFile)) {
                            continue
                        }

                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: canonicalFile.nameWithoutExtension
                        val artist = cursor.getString(artistCol) ?: "<Unknown Artist>"
                        val album = cursor.getString(albumCol) ?: "<Unknown Album>"
                        val duration = cursor.getLong(durationCol)
                        val size = cursor.getLong(sizeCol)

                        rawTracks.add(
                            AudioTrack(
                                id = id,
                                title = title,
                                artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                                album = if (album == "<unknown>") "Unknown Album" else album,
                                path = canonicalPath,
                                duration = duration,
                                size = size,
                                format = ext.uppercase()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioScanner", "Error querying media store: ${e.message}")
            }
        }

        // Deduplicate tracks strictly by normalized canonical path
        val uniqueTracks = rawTracks.distinctBy { it.path }

        return buildFolderHierarchy(uniqueTracks, context)
    }

    /**
     * Builds a clean nested folder hierarchy from audio tracks with absolute deduplication
     * and automatic pruning of empty directories.
     */
    private fun buildFolderHierarchy(tracks: List<AudioTrack>, context: Context): List<FolderNode> {
        val folderMap = mutableMapOf<String, FolderNode>()

        // 1. Group tracks strictly by parent directory's canonical path
        for (track in tracks) {
            val file = File(track.path)
            val parentFile = try { file.parentFile?.canonicalFile } catch (e: Exception) { file.parentFile } ?: continue
            val parentCanonicalPath = parentFile.path

            val folderNode = folderMap.getOrPut(parentCanonicalPath) {
                FolderNode(name = parentFile.name, path = parentCanonicalPath)
            }
            if (folderNode.tracks.none { it.path == track.path }) {
                folderNode.tracks.add(track)
            }
        }

        // 2. Connect parent folders in hierarchy up to storage root
        val allFolderPaths = folderMap.keys.toList()
        for (path in allFolderPaths) {
            var currentFile = File(path)
            var childNode = folderMap[path] ?: continue

            while (true) {
                val parentFile = try { currentFile.parentFile?.canonicalFile } catch (e: Exception) { currentFile.parentFile } ?: break
                val parentCanonicalPath = parentFile.path

                // Stop traversing if we hit root directory, system storage root, or volume boundary
                if (parentCanonicalPath == "/" ||
                    parentCanonicalPath == "/storage" ||
                    currentFile.name == "0" ||
                    currentFile.name == "emulated"
                ) {
                    break
                }

                val parentNode = folderMap.getOrPut(parentCanonicalPath) {
                    FolderNode(name = parentFile.name, path = parentCanonicalPath)
                }

                if (parentNode.subFolders.none { it.path == childNode.path }) {
                    parentNode.subFolders.add(childNode)
                }

                childNode = parentNode
                currentFile = parentFile
            }
        }

        // 3. Find root folders (nodes whose parents are not in the folderMap or are root storage paths)
        val candidateRoots = folderMap.values.filter { node ->
            val file = File(node.path)
            val parentFile = try { file.parentFile?.canonicalFile } catch (e: Exception) { file.parentFile }
            parentFile == null ||
                    parentFile.path == "/" ||
                    parentFile.path == "/storage" ||
                    parentFile.name == "0" ||
                    parentFile.name == "emulated" ||
                    !folderMap.containsKey(parentFile.path)
        }.distinctBy { it.path }

        // 4. Recursive empty folder pruning and deduplication
        fun pruneAndCleanHierarchy(node: FolderNode): Boolean {
            val subIterator = node.subFolders.iterator()
            val seenSubPaths = mutableSetOf<String>()
            val cleanedSubs = mutableListOf<FolderNode>()

            while (subIterator.hasNext()) {
                val sub = subIterator.next()
                if (seenSubPaths.add(sub.path)) {
                    val hasValidDescendants = pruneAndCleanHierarchy(sub)
                    if (hasValidDescendants) {
                        cleanedSubs.add(sub)
                    }
                }
            }

            node.subFolders.clear()
            node.subFolders.addAll(cleanedSubs)
            node.subFolders.sortBy { it.name.lowercase() }

            val uniqueTracks = node.tracks.distinctBy { it.path }
            node.tracks.clear()
            node.tracks.addAll(uniqueTracks)
            node.tracks.sortBy { it.title.lowercase() }

            // A folder is retained only if it contains audio tracks or non-empty subfolders
            return node.tracks.isNotEmpty() || node.subFolders.isNotEmpty()
        }

        val validRoots = candidateRoots.filter { pruneAndCleanHierarchy(it) }

        // 5. Wrap inside clean storage root groupings if external volumes are present
        val storageDir = File("/storage")
        val extVolumes = if (storageDir.exists() && storageDir.isDirectory) {
            storageDir.listFiles()?.filter {
                it.isDirectory &&
                        it.name != "emulated" &&
                        it.name != "self" &&
                        !it.name.startsWith(".")
            } ?: emptyList()
        } else {
            emptyList()
        }

        if (extVolumes.isNotEmpty()) {
            val internalRoot = FolderNode(name = "Internal Storage", path = "/storage/emulated/0")
            val externalRoots = extVolumes.map { volume ->
                val vCanon = try { volume.canonicalPath } catch (e: Exception) { volume.absolutePath }
                FolderNode(name = "External Storage (${volume.name})", path = vCanon)
            }

            for (root in validRoots) {
                if (root.path.startsWith("/storage/emulated/0")) {
                    if (internalRoot.subFolders.none { it.path == root.path }) {
                        internalRoot.subFolders.add(root)
                    }
                } else {
                    val matchingVolume = externalRoots.find { root.path.startsWith(it.path) }
                    if (matchingVolume != null) {
                        if (matchingVolume.subFolders.none { it.path == root.path }) {
                            matchingVolume.subFolders.add(root)
                        }
                    } else {
                        if (internalRoot.subFolders.none { it.path == root.path }) {
                            internalRoot.subFolders.add(root)
                        }
                    }
                }
            }

            val finalRoots = mutableListOf<FolderNode>()
            if (internalRoot.subFolders.isNotEmpty() || internalRoot.tracks.isNotEmpty()) {
                finalRoots.add(internalRoot)
            }
            for (ext in externalRoots) {
                if (ext.subFolders.isNotEmpty() || ext.tracks.isNotEmpty()) {
                    finalRoots.add(ext)
                }
            }
            return finalRoots.distinctBy { it.path }.sortedBy { it.name.lowercase() }
        } else {
            return validRoots.distinctBy { it.path }.sortedBy { it.name.lowercase() }
        }
    }
}

