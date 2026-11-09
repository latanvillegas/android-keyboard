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
import org.futo.inputmethod.latin.uix.actions.media.FluentHqPackStore
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Non-exported read-only content provider for high-quality Microsoft Fluent art.
 *
 *  - Lossless 256px RGBA Animated WebP (preferred).
 *  - 256px palette GIF only for compatible legacy receivers.
 *  - PNG created without loss from a 256px RGBA static master.
 */
internal object Emoji3DShareStore {
    val AUTHORITY: String = BuildConfig.APPLICATION_ID + ".emoji3d"
    const val MIME_TYPE = "image/png"
    const val ANIMATED_MIME_TYPE = "image/gif"
    const val ANIMATED_WEBP_MIME_TYPE = "image/webp"

    private const val DIRECTORY = "emoji3d-shares"
    private const val CACHE_VERSION = "fluent3d-rgba256-v2"
    private const val MAX_FILE_BYTES = 16L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    private const val EXPIRY_MILLIS = 24L * 60 * 60 * 1000
    private const val MIN_RETENTION_MILLIS = 10L * 60 * 1000
    private val guard = Any()
    private val idPattern = Regex("[0-9a-f]{64}")

    private enum class Format(
        val mime: String, val extension: String, val suffix: String?
    ) {
        PNG(MIME_TYPE, "png", null),
        GIF(ANIMATED_MIME_TYPE, "gif", "animated"),
        WEBP(ANIMATED_WEBP_MIME_TYPE, "webp", "animated-webp")
    }

    private fun directory(context: Context): File =
        File(context.cacheDir, DIRECTORY)

    private fun assetId(context: Context, key: String, format: Format): String {
        val value = "$CACHE_VERSION:" + FluentHqPackStore.contentVersion(context) +
            ":$key:" + format.extension
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        val digits = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val n = byte.toInt() and 0xff
                append(digits[n ushr 4])
                append(digits[n and 0x0f])
            }
        }
    }

    fun prepare(
        context: Context, emoji: String, category: String?, description: String?
    ): Uri? = prepareInternal(context, emoji, category, description, Format.PNG)

    fun prepareAnimation(
        context: Context, emoji: String, category: String?, description: String?
    ): Uri? = prepareInternal(context, emoji, category, description, Format.GIF)

    fun prepareWebpAnimation(
        context: Context, emoji: String, category: String?, description: String?
    ): Uri? = prepareInternal(context, emoji, category, description, Format.WEBP)

    /** Must run on Dispatchers.IO. Files become visible only after completion. */
    private fun prepareInternal(
        context: Context,
        emoji: String,
        category: String?,
        description: String?,
        format: Format
    ): Uri? {
        val app = context.applicationContext
        val key = FluentEmoji3D.assetKey(app, emoji, category, description)
            ?: return null
        if (format != Format.PNG && !FluentEmoji3DMotion.hasAnimation(app, key)) {
            return null
        }

        val id = assetId(app, key, format)
        synchronized(guard) {
            val dir = directory(app)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IOException("Cannot create Fluent share cache")
            }

            val output = File(dir, "$id." + format.extension)
            if (output.isFile && !isValidFile(output, format)) {
                output.delete()
            }

            if (!output.isFile) {
                val temporary = File.createTempFile("emoji3d-", ".tmp", dir)
                try {
                    when (format) {
                        Format.PNG -> {
                            val bitmap = FluentEmoji3D.loadShareBitmap(
                                app, emoji, category, description
                            ) ?: return null
                            FileOutputStream(temporary).use { stream ->
                                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                                    throw IOException("Lossless PNG compression failed")
                                }
                            }
                        }
                        Format.GIF, Format.WEBP -> {
                            val source = if (format == Format.WEBP) {
                                FluentEmoji3DMotion.openWebp(app, key)
                            } else {
                                FluentEmoji3DMotion.openGif(app, key)
                            } ?: return null

                            source.use { input ->
                                FileOutputStream(temporary).use { outputStream ->
                                    val buffer = ByteArray(16 * 1024)
                                    var bytes = 0L
                                    while (true) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        bytes += read
                                        if (bytes > MAX_FILE_BYTES) {
                                            throw IOException("Animated share exceeds size limit")
                                        }
                                        outputStream.write(buffer, 0, read)
                                    }
                                }
                            }
                        }
                    }

                    if (!isValidFile(temporary, format)) {
                        throw IOException("Invalid or oversized Fluent media")
                    }
                    if (!temporary.renameTo(output)) {
                        throw IOException("Cannot publish Fluent media")
                    }
                } finally {
                    temporary.delete()
                }
            }

            if (!isValidFile(output, format)) {
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
            format.suffix?.let { builder.appendPath(it) }
            return builder.build()
        }
    }

    private fun isValidFile(file: File, format: Format): Boolean {
        if (!file.isFile || file.length() !in 1L..MAX_FILE_BYTES) return false
        return try {
            file.inputStream().use { stream ->
                val header = ByteArray(12)
                var filled = 0
                while (filled < header.size) {
                    val n = stream.read(header, filled, header.size - filled)
                    if (n < 0) break
                    filled += n
                }
                if (filled < 12) return@use false
                when (format) {
                    Format.PNG -> header.copyOfRange(0, 8).contentEquals(
                        byteArrayOf(
                            0x89.toByte(), 0x50, 0x4e, 0x47,
                            0x0d, 0x0a, 0x1a, 0x0a
                        )
                    )
                    Format.GIF -> {
                        val magic = String(header, 0, 6, StandardCharsets.US_ASCII)
                        magic == "GIF89a" || magic == "GIF87a"
                    }
                    Format.WEBP -> {
                        String(header, 0, 4, StandardCharsets.US_ASCII) == "RIFF" &&
                            String(header, 8, 4, StandardCharsets.US_ASCII) == "WEBP"
                    }
                }
            }
        } catch (_: IOException) {
            false
        }
    }

    private fun trimCache(directory: File, protectedFile: File) {
        val now = System.currentTimeMillis()
        val files = directory.listFiles { file ->
            file.isFile && file.extension in setOf("png", "gif", "webp")
        }?.sortedBy { it.lastModified() } ?: return

        var total = files.sumOf { it.length() }
        for (file in files) {
            if (file == protectedFile) continue
            val age = (now - file.lastModified()).coerceAtLeast(0L)
            if (age >= EXPIRY_MILLIS ||
                (total > MAX_CACHE_BYTES && age >= MIN_RETENTION_MILLIS)
            ) {
                val previous = file.length()
                if (file.delete()) total -= previous
            }
        }
    }

    private fun formatFor(uri: Uri): Format {
        val segments = uri.pathSegments
        if (uri.scheme != "content" || uri.authority != AUTHORITY ||
            uri.query != null || uri.fragment != null ||
            (segments.size != 2 && segments.size != 3) ||
            segments[0] != "emoji" || !idPattern.matches(segments[1])
        ) {
            throw FileNotFoundException("Invalid Fluent 3D content URI")
        }
        return if (segments.size == 2) {
            Format.PNG
        } else {
            when (segments[2]) {
                "animated" -> Format.GIF
                "animated-webp" -> Format.WEBP
                else -> throw FileNotFoundException("Unknown Fluent 3D media type")
            }
        }
    }

    fun mimeFor(uri: Uri): String = formatFor(uri).mime

    fun fileFor(context: Context, uri: Uri): File {
        val format = formatFor(uri)
        return File(directory(context), uri.pathSegments[1] + "." + format.extension)
    }
}

class Emoji3DContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String =
        Emoji3DShareStore.mimeFor(uri)

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only Fluent 3D provider")
        val file = Emoji3DShareStore.fileFor(requireNotNull(context), uri)
        if (!file.isFile || file.length() <= 0L) {
            throw FileNotFoundException("Fluent 3D media unavailable")
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
        val file = Emoji3DShareStore.fileFor(requireNotNull(context), uri)
        if (!file.isFile) return null
        val columns = projection?.filterNotNull()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val displayName = when (Emoji3DShareStore.mimeFor(uri)) {
            Emoji3DShareStore.ANIMATED_WEBP_MIME_TYPE -> "emoji-3d.webp"
            Emoji3DShareStore.ANIMATED_MIME_TYPE -> "emoji-3d.gif"
            else -> "emoji-3d.png"
        }
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> displayName
                OpenableColumns.SIZE -> file.length()
                else -> null
            }
        }.toTypedArray()
        return MatrixCursor(columns).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only Fluent 3D provider")

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String?>?
    ): Int = throw UnsupportedOperationException("Read-only Fluent 3D provider")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String?>?): Int =
        throw UnsupportedOperationException("Read-only Fluent 3D provider")
}
