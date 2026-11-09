package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Persistent user-owned files: never bundled into the APK or re-encoded on import. */
internal data class MediaItem(
    val id: String,
    val kind: MediaKind,
    val displayName: String,
    val length: Long,
    val createdAt: Long,
    val favorite: Boolean
)

internal object MediaLibrary {
    const val MAX_ITEM_BYTES: Long = 24L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    private const val MAX_ITEMS = 500
    private const val MAX_SIDE = 4096
    private const val MAX_PIXELS = 16_777_216L
    private val idPattern = Regex("[0-9a-f]{64}")
    private val lock = Any()

    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val previews = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.byteCount + 1023) / 1024
    }

    private fun root(context: Context) =
        File(context.applicationContext.noBackupFilesDir, "media-hub")

    private fun catalog(context: Context) =
        AtomicFile(File(root(context), "catalog.json"))

    private fun normalizeName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
            .filter { it.code >= 32 && it.code != 127 }
            .take(100)
        return base.ifBlank { "Imported media" }
    }

    private fun entries(context: Context): MutableList<MediaItem> {
        val file = catalog(context)
        val text = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: java.io.FileNotFoundException) {
            return mutableListOf()
        }
        val json = JSONArray(text)
        if (json.length() > MAX_ITEMS) throw IOException("Invalid media catalog size")
        return MutableList(json.length()) { index ->
            val obj = json.getJSONObject(index)
            val id = obj.getString("id")
            if (!idPattern.matches(id)) throw IOException("Invalid media identifier")
            val kind = MediaKind.valueOf(obj.getString("format"))
            MediaItem(
                id = id,
                kind = kind,
                displayName = normalizeName(obj.getString("name")),
                length = obj.getLong("bytes"),
                createdAt = obj.getLong("createdAt"),
                favorite = obj.optBoolean("favorite", false)
            )
        }
    }

    private fun write(context: Context, items: List<MediaItem>) {
        val root = root(context)
        if (!root.isDirectory && !root.mkdirs()) throw IOException("Media directory unavailable")
        val json = JSONArray()
        for (item in items) {
            json.put(JSONObject().apply {
                put("id", item.id)
                put("format", item.kind.name)
                put("name", item.displayName)
                put("bytes", item.length)
                put("createdAt", item.createdAt)
                put("favorite", item.favorite)
            })
        }
        val file = catalog(context)
        val output = file.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    /** Enumerate metadata only; missing or corrupt files never become shareable URIs. */
    suspend fun list(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            entries(context).filter { item -> fileFor(context, item).isFile }
                .sortedWith(compareByDescending<MediaItem> { it.favorite }
                    .thenByDescending { it.createdAt })
        }
    }

    fun get(context: Context, id: String): MediaItem? = synchronized(lock) {
        if (!idPattern.matches(id)) return@synchronized null
        entries(context).firstOrNull { it.id == id }
            ?.takeIf { fileFor(context, it).isFile }
    }

    fun fileFor(context: Context, item: MediaItem): File {
        require(idPattern.matches(item.id)) { "Invalid media id" }
        return File(root(context), item.id + "." + item.kind.extension)
    }

    fun importedName(context: Context, uri: Uri): String {
        val resolved = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        return normalizeName(resolved ?: (uri.lastPathSegment ?: "Imported media"))
    }

    fun importUri(context: Context, uri: Uri): MediaItem {
        if (uri.scheme != "content") throw IOException("Only content URI imports supported")
        val name = importedName(context, uri)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open selected file")
        return input.use { importStream(context, it, name) }
    }

    /**
     * Bounded copy with SHA-256 naming; caller owns source stream (also works on
     * ZipInputStream entries). Never consumes or stores external filesystem paths.
     * Call only from Dispatchers.IO.
     */
    fun importStream(context: Context, source: InputStream, name: String): MediaItem =
        synchronized(lock) {
            val directory = root(context)
            if (!directory.isDirectory && !directory.mkdirs())
                throw IOException("Cannot create media directory")

            val staging = File.createTempFile("import-", ".pending", directory)
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                staging.outputStream().buffered().use { output ->
                    val chunk = ByteArray(32 * 1024)
                    while (true) {
                        val count = source.read(chunk)
                        if (count < 0) break
                        if (count == 0) continue
                        total += count
                        if (total > MAX_ITEM_BYTES)
                            throw IOException("Media exceeds 24 MiB limit")
                        output.write(chunk, 0, count)
                        digest.update(chunk, 0, count)
                    }
                }
                val header = ByteArray(16)
                FileInputStream(staging).use { stream ->
                    var filled = 0
                    while (filled < header.size) {
                        val read = stream.read(header, filled, header.size - filled)
                        if (read < 0) break
                        filled += read
                    }
                }
                val kind = MediaMagic.detect(header)
                    ?: throw IOException("Only genuine PNG, JPEG, GIF and WebP images are supported")

                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(staging.absolutePath, options)
                val width = options.outWidth
                val height = options.outHeight
                if (width < 1 || height < 1 || width > MAX_SIDE ||
                    height > MAX_SIDE || width.toLong() * height > MAX_PIXELS) {
                    throw IOException("Unsupported image dimensions")
                }

                val id = digest.digest().joinToString("") { byte ->
                    "%02x".format(byte.toInt() and 0xff)
                }
                val saved = entries(context)
                saved.firstOrNull { it.id == id }?.let { existing ->
                    if (fileFor(context, existing).isFile) return@synchronized existing
                }

                if (saved.size >= MAX_ITEMS ||
                    saved.sumOf { it.length } + total > MAX_TOTAL_BYTES) {
                    throw IOException("Media library quota reached")
                }
                val target = File(directory, id + "." + kind.extension)
                if (!staging.renameTo(target))
                    throw IOException("Cannot atomically save imported media")

                val item = MediaItem(
                    id, kind, normalizeName(name), total, System.currentTimeMillis(), false
                )
                val revised = saved.filterNot { it.id == id } + item
                try {
                    write(context, revised)
                } catch (error: Throwable) {
                    target.delete()
                    throw error
                }
                changes.tryEmit(Unit)
                return@synchronized item
            } finally {
                staging.delete()
            }
        }

    suspend fun setFavorite(context: Context, id: String, value: Boolean) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val items = entries(context)
                if (items.none { it.id == id }) return@synchronized
                write(context, items.map {
                    if (it.id == id) it.copy(favorite = value) else it
                })
                changes.tryEmit(Unit)
            }
        }

    suspend fun delete(context: Context, id: String) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val items = entries(context)
            val item = items.firstOrNull { it.id == id } ?: return@synchronized
            write(context, items.filterNot { it.id == id })
            fileFor(context, item).delete()
            previews.remove(id)
            changes.tryEmit(Unit)
        }
    }

    fun thumbnail(context: Context, item: MediaItem): Bitmap? {
        synchronized(previews) { previews.get(item.id)?.let { return it } }
        val file = fileFor(context, item)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 160 || bounds.outHeight / sample > 160)
            sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val result = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        synchronized(previews) { previews.put(item.id, result) }
        return result
    }
}
