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
 * Read-only, temporary, offline PNG export of the same Fluent artwork shown in EmojiView.
 * The provider is non-exported; URI access is granted only to the focused editor.
 */
internal object Emoji3DShareStore {
    const val AUTHORITY: String = BuildConfig.APPLICATION_ID + ".emoji3d"
    const val MIME_TYPE: String = "image/png"

    private const val DIRECTORY = "emoji3d-shares"
    private const val MAX_PNG_BYTES = 8L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    private const val EXPIRY_MILLIS = 24L * 60 * 60 * 1000
    private const val MIN_RETENTION_MILLIS = 10L * 60 * 1000
    private val guard = Any()
    private val idPattern = Regex("[0-9a-f]{64}")

    private fun directory(context: Context): File = File(context.cacheDir, DIRECTORY)

    private fun assetId(assetKey: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(assetKey.toByteArray(StandardCharsets.UTF_8))
        val digits = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val octet = byte.toInt() and 0xff
                append(digits[octet ushr 4])
                append(digits[octet and 0x0f])
            }
        }
    }

    /**
     * Must be called from Dispatchers.IO. A successful result refers to a complete,
     * immutable PNG, never a partially written output.
     */
    fun prepare(context: Context, emoji: String, category: String?, description: String?): Uri? {
        val app = context.applicationContext
        val key = FluentEmoji3D.assetKey(app, emoji, category, description) ?: return null
        val id = assetId(key)

        synchronized(guard) {
            val dir = directory(app)
            if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create emoji share cache")
            val output = File(dir, "$id.png")
            if (!output.isFile || output.length() == 0L) {
                val bitmap = FluentEmoji3D.loadBitmap(app, emoji, category, description)
                    ?: return null
                val temporary = File.createTempFile("emoji3d-", ".tmp", dir)
                try {
                    FileOutputStream(temporary).use { stream ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                            throw IOException("PNG compression failed")
                        }
                    }
                    if (temporary.length() !in 1..MAX_PNG_BYTES) {
                        throw IOException("Emoji PNG exceeds sharing limit")
                    }
                    if (!temporary.renameTo(output)) {
                        throw IOException("Cannot publish emoji PNG")
                    }
                } finally {
                    temporary.delete()
                }
            }
            if (output.length() !in 1..MAX_PNG_BYTES) {
                output.delete()
                return null
            }
            output.setLastModified(System.currentTimeMillis())
            trimCache(dir, output)

            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("emoji")
                .appendPath(id)
                .build()
        }
    }

    private fun trimCache(dir: File, current: File) {
        val now = System.currentTimeMillis()
        val images = dir.listFiles { candidate -> candidate.isFile && candidate.extension == "png" }
            ?.sortedBy { it.lastModified() } ?: return

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

    fun fileFor(context: Context, uri: Uri): File {
        val path = uri.pathSegments
        if (uri.scheme != "content" || uri.authority != AUTHORITY ||
            path.size != 2 || path[0] != "emoji" || !idPattern.matches(path[1])
        ) {
            throw FileNotFoundException("Invalid emoji content URI")
        }
        return File(directory(context), path[1] + ".png")
    }
}

class Emoji3DContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String {
        Emoji3DShareStore.fileFor(requireNotNull(context), uri)
        return Emoji3DShareStore.MIME_TYPE
    }

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
        val file = Emoji3DShareStore.fileFor(requireNotNull(context), uri)
        if (!file.isFile) return null
        val columns = projection?.filterNotNull()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = columns.map { column ->
            when (column) {
                OpenableColumns.DISPLAY_NAME -> "emoji-3d.png"
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
