package org.futo.inputmethod.latin.uix.actions.media

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import org.futo.inputmethod.latin.BuildConfig
import java.io.FileNotFoundException

/** A read-only ContentProvider for original imported files; never exported. */
class MediaShareProvider : ContentProvider() {
    companion object {
        private val authority get() = BuildConfig.APPLICATION_ID + ".user-media"
        private val idPattern = Regex("[0-9a-f]{64}")
        fun uri(id: String): Uri {
            require(idPattern.matches(id)) { "Invalid media identifier" }
            return Uri.Builder().scheme("content").authority(authority)
                .appendPath("media").appendPath(id).build()
        }
    }

    override fun onCreate(): Boolean = true

    private fun resolve(uri: Uri): MediaItem {
        val parts = uri.pathSegments
        if (uri.scheme != "content" || uri.authority != authority ||
            uri.query != null || uri.fragment != null ||
            parts.size != 2 || parts[0] != "media" ||
            !idPattern.matches(parts[1])) {
            throw FileNotFoundException("Invalid media content URI")
        }
        return MediaLibrary.get(requireNotNull(context), parts[1])
            ?: throw FileNotFoundException("Imported media unavailable")
    }

    override fun getType(uri: Uri): String = resolve(uri).kind.mime

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Media provider is read-only")
        val item = resolve(uri)
        val file = MediaLibrary.fileFor(requireNotNull(context), item)
        if (!file.isFile || file.length() < 1L)
            throw FileNotFoundException("Media file missing")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, projection: Array<out String?>?,
                       selection: String?, selectionArgs: Array<out String?>?,
                       sortOrder: String?): Cursor {
        val item = resolve(uri)
        val cols = projection?.filterNotNull()?.toTypedArray()
            ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply {
            addRow(cols.map { col ->
                when (col) {
                    OpenableColumns.DISPLAY_NAME -> item.displayName
                    OpenableColumns.SIZE -> item.length
                    else -> null
                }
            }.toTypedArray())
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? =
        throw UnsupportedOperationException("Read-only media provider")
    override fun update(uri: Uri, values: ContentValues?, selection: String?,
                        selectionArgs: Array<out String?>?): Int =
        throw UnsupportedOperationException("Read-only media provider")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String?>?): Int =
        throw UnsupportedOperationException("Read-only media provider")
}
