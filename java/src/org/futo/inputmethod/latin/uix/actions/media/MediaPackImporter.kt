package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Safe bounded ZIP image import. No filesystem extraction or path traversal. */
internal object MediaPackImporter {
    private const val MAX_ZIP_MEMBERS = 200
    private const val MAX_COMPRESSED_BYTES = 128L * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 256L * 1024 * 1024
    private val member = Regex(
        "(?:media/)?[A-Za-z0-9_. -]{1,100}\\.(?:png|jpe?g|gif|webp)",
        RegexOption.IGNORE_CASE
    )

    private class BoundedInputStream(source: InputStream) : FilterInputStream(source) {
        var consumed = 0L
            private set
        override fun read(): Int {
            val result = super.read()
            if (result >= 0) verify(1)
            return result
        }
        override fun read(bytes: ByteArray, off: Int, len: Int): Int {
            val count = super.read(bytes, off, len)
            if (count > 0) verify(count)
            return count
        }
        private fun verify(length: Int) {
            consumed += length
            if (consumed > MAX_COMPRESSED_BYTES)
                throw IOException("Media ZIP exceeds compressed size limit")
        }
    }

    fun import(context: Context, uri: Uri): Int {
        if (uri.scheme != "content") throw IOException("Only content URIs supported")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open ZIP package")
        var imported = 0
        var members = 0
        var bytes = 0L
        BoundedInputStream(BufferedInputStream(input)).use { limited ->
            ZipInputStream(limited).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    members++
                    if (members > MAX_ZIP_MEMBERS) throw IOException("Too many ZIP entries")
                    if (!entry.isDirectory && member.matches(entry.name)) {
                        if (entry.size > MediaLibrary.MAX_ITEM_BYTES)
                            throw IOException("ZIP media exceeds single-file limit")
                        val item = MediaLibrary.importStream(
                            context, archive, entry.name.substringAfterLast('/')
                        )
                        bytes += item.length
                        if (bytes > MAX_UNCOMPRESSED_BYTES)
                            throw IOException("Media ZIP exceeds expanded size limit")
                        imported++
                    }
                    archive.closeEntry()
                }
            }
        }
        if (imported == 0) throw IOException("No supported images found in ZIP")
        return imported
    }

    fun hasZipSignature(context: Context, uri: Uri): Boolean {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val magic = ByteArray(4)
            val count = input.read(magic)
            return count == 4 && MediaMagic.isZip(magic)
        }
        return false
    }
}
