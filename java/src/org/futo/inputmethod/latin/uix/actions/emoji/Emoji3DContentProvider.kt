package org.futo.inputmethod.latin.uix.actions.emoji

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.futo.inputmethod.latin.BuildConfig
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Private, read-only URI service for exact Fluent artwork.
 * PNG remains the static fallback; GIF contains genuine Microsoft animation
 * frames packaged with the APK. No external file paths are accepted.
 */
internal object Emoji3DShareStore {
    val AUTHORITY: String = BuildConfig.APPLICATION_ID + ".emoji3d"
    const val MIME_TYPE = "image/png"
    const val ANIMATED_MIME_TYPE = "image/gif"

    private const val DIRECTORY = "emoji3d-shares"
    private const val MAX_FILE_BYTES = 8L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    private const val EXPIRY_MILLIS = 24L * 60 * 60 * 1000
    private const val MIN_RETENTION_MILLIS = 10L * 60 * 1000
    private val guard = Any()
    private val idPattern = Regex("[0-9a-f]{64}")

    private fun directory(context: Context) = File(context.cacheDir, DIRECTORY)

    private fun assetId(key: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(StandardCharsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val n = byte.toInt() and 0xff
                append(hex[n ushr 4])
                append(hex[n and 0x0f])
            }
        }
    }

    /** Prepare a static transparent PNG on Dispatchers.IO. */
    fun prepare(context: Context, emoji: String, category: String?, description: String?): Uri? =
        prepareInternal(context, emoji, category, description, animated = false)

    /** Prepare a genuine animated GIF on Dispatchers.IO, or null if none was bundled. */
    fun prepareAnimation(
        context: Context,
        emoji: String,
        category: String?,
        description: String?
    ): Uri? = prepareInternal(context, emoji, category, description, animated = true)

    private fun prepareInternal(
        context: Context,
        emoji: String,
        category: String?,
        description: String?,
        animated: Boolean
    ): Uri? {
        val app = context.applicationContext
        val key = FluentEmoji3D.assetKey(app, emoji, category, description) ?: return null
        if (animated && !FluentEmoji3DMotion.hasAnimation(app, key)) return null
        val id = assetId(key)
        val extension = if (animated) "gif" else "png"

        synchronized(guard) {
            val dir = directory(app)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IOException("Cannot create emoji share cache")
            }
            val output = File(dir, "$id.$extension")
            if (output.isFile && output.length() !in 1L..MAX_FILE_BYTES) {
                output.delete()
            }

            if (!output.isFile) {
                val temporary = File.createTempFile("emoji3d-", ".tmp", dir)
                try {
                    if (animated) {
                        val source = FluentEmoji3DMotion.openGif(app, key) ?: return null
                        source.use { input ->
                            FileOutputStream(temporary).use { stream ->
                                val buffer = ByteArray(16 * 1024)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count == -1) break
                                    total += count
                                    if (total > MAX_FILE_BYTES) {
                                        throw IOException("Animated emoji exceeds 8 MiB")
                                    }
                                    stream.write(buffer, 0, count)
                                }
                                if (total < 6L) throw IOException("Empty animated emoji")
                            }
                        }
                        // Reject missing assets or Git LFS pointer files.
                        temporary.inputStream().use { input ->
                            val header = ByteArray(6)
                            if (input.read(header) != 6 ||
                                !(String(header, StandardCharsets.US_ASCII) == "GIF89a" ||
                                    String(header, StandardCharsets.US_ASCII) == "GIF87a")
                            ) {
                                throw IOException("Bundled animation is not GIF")
                            }
                        }
                    } else {
                        val bitmap = FluentEmoji3D.loadBitmap(app, emoji, category, description)
                            ?: return null
                        FileOutputStream(temporary).use { stream ->
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                throw IOException("PNG compression failed")
                            }
                        }
                    }
                    if (temporary.length() !in 1L..MAX_FILE_BYTES) {
                        throw IOException("Emoji export exceeds size limit")
                    }
                    if (!temporary.renameTo(output)) {
                        throw IOException("Cannot publish emoji export")
                    }
                } finally {
                    temporary.delete()
                }
            }

            if (output.length() !in 1L..MAX_FILE_BYTES) {
                output.delete()
                return null
            }
            output.setLastModified(System.currentTimeMillis())
            trimCache(dir, output)

            val builder = Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("emoji")
                .appendPath(id)
            if (animated) builder.appendPath("animated")
            return builder.build()
        }
    }

    private fun trimCache(dir: File, current: File) {
        val now = System.currentTimeMillis()
        val images = dir.listFiles { file ->
            file.isFile && (file.extension == "png" || file.extension == "gif")
        }?.sortedBy { it.lastModified() } ?: return

        var total = images.sumOf { it.length() }
        for (image in images) {
            if (image == current) continue
            val age = (now - image.lastModified()).coerceAtLeast(0L)
            if (age >= EXPIRY_MILLIS ||
                (total > MAX_CACHE_BYTES && age >= MIN_RETENTION_MILLIS)
            ) {
                val size = image.length()
                if (image.delete()) total -= size
            }
        }
    }

    /** Validates the entire URI before turning it into a private cache path. */
    fun fileFor(context: Context, uri: Uri): File {
        val segments = uri.pathSegments
        if (uri.scheme != "content" || uri.authority != AUTHORITY ||
            (segments.size != 2 && segments.size != 3) ||
            segments[0] != "emoji" || !idPattern.matches(segments[1]) ||
            (segments.size == 3 && segments[2] != "animated")
        ) {
            throw FileNotFoundException("Invalid emoji content URI")
        }
        val extension = if (segments.size == 3) "gif" else "png"
        return File(directory(context), segments[1] + "." + extension)
    }

    fun mimeFor(context: Context, uri: Uri): String {
        fileFor(context, uri)
        return if (uri.pathSegments.size == 3) ANIMATED_MIME_TYPE else MIME_TYPE
    }
}

class Emoji3DContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String =
        Emoji3DShareStore.mimeFor(requireNotNull(context), uri)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only emoji provider")
        val file = Emoji3DShareStore.fileFor(requireNotNull(context), uri)
        if (!file.isFile || file.length() <= 0L) {
            throw FileNotFoundException("Emoji image unavailable")
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String?>?,
        selection: String?,
        selectionArgs: Array<out String?>?,
        sortOrder: String?
    ): Cursor? {
        val app = requireNotNull(context)
        val file = Emoji3DShareStore.fileFor(app, uri)
        if (!file.isFile) return null
        val columns = projection?.filterNotNull()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val name = if (Emoji3DShareStore.mimeFor(app, uri) ==
            Emoji3DShareStore.ANIMATED_MIME_TYPE) "emoji-3d.gif" else "emoji-3d.png"
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> name
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only emoji provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int = throw UnsupportedOperationException("Read-only emoji provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String?>?): Int =
        throw UnsupportedOperationException("Read-only emoji provider")
}
