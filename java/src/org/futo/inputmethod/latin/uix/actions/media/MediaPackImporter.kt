package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

/**
 * Bounded portable ZIP import: raw bytes copied by MediaLibrary, no
 * untrusted archive paths ever used to create directories or files.
 */
internal object MediaPackImporter {
    private const val MAX_ZIP_MEMBERS = 600
    private const val MAX_COMPRESSED_BYTES = 528L * 1024 * 1024
    private const val MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 256 * 1024
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
                throw IOException("ZIP exceeds 528 MiB compressed limit")
        }
    }

    private data class EntryInfo(val name: String, val favorite: Boolean)

    /** Manifest is optional for external image ZIPs. Invalid explicit packs fail. */
    private fun readManifest(stream: ZipInputStream): Map<String, EntryInfo> {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            bytes.write(buffer, 0, count)
            if (bytes.size() > MAX_MANIFEST_BYTES)
                throw IOException("Oversized media manifest")
        }
        val json = JSONObject(bytes.toString("UTF-8"))
        if (json.optString("format") != "futo-media-pack" ||
            json.optInt("version") != 1) {
            throw IOException("Unsupported media pack version")
        }
        val items = json.getJSONArray("items")
        if (items.length() > 500) throw IOException("Too many media pack items")
        return buildMap {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val path = item.getString("path")
                if (!member.matches(path)) throw IOException("Invalid media pack path")
                put(path, EntryInfo(item.optString("name", path.substringAfterLast('/')),
                    item.optBoolean("favorite", false)))
            }
        }
    }

    suspend fun importPack(context: Context, uri: Uri): Int {
        if (uri.scheme != "content") throw IOException("Only content URIs supported")
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open ZIP package")
        var imported = 0
        var members = 0
        var expanded = 0L
        var manifest = emptyMap<String, EntryInfo>()
        BoundedInputStream(BufferedInputStream(input)).use { limited ->
            ZipInputStream(limited).use { archive ->
                while (true) {
                    val entry = archive.nextEntry ?: break
                    members++
                    if (members > MAX_ZIP_MEMBERS) throw IOException("Too many ZIP entries")
                    if (!entry.isDirectory && entry.name == "pack.json") {
                        manifest = readManifest(archive)
                    } else if (!entry.isDirectory && member.matches(entry.name)) {
                        if (entry.size > MediaLibrary.MAX_ITEM_BYTES)
                            throw IOException("Image exceeds single-file limit")
                        val metadata = manifest[entry.name]
                        val item = MediaLibrary.importStream(
                            context, archive,
                            metadata?.name ?: entry.name.substringAfterLast('/')
                        )
                        expanded += item.length
                        if (expanded > MAX_UNCOMPRESSED_BYTES)
                            throw IOException("Media pack exceeds expanded size limit")
                        if (metadata?.favorite == true)
                            MediaLibrary.setFavorite(context, item.id, true)
                        imported++
                    } else {
                        // Drain unknown entries with an expanded-byte budget too:
                        // ZIP bombs must not hide behind skipped entries.
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = archive.read(buffer)
                            if (count < 0) break
                            expanded += count
                            if (expanded > MAX_UNCOMPRESSED_BYTES)
                                throw IOException("ZIP expansion limit exceeded")
                        }
                    }
                    archive.closeEntry()
                }
            }
        }
        if (imported == 0) throw IOException("No supported media in ZIP")
        return imported
    }

    fun hasZipSignature(context: Context, uri: Uri): Boolean {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val magic = ByteArray(4)
            var n = 0
            while (n < magic.size) {
                val count = input.read(magic, n, magic.size - n)
                if (count < 0) break
                n += count
            }
            return n == 4 && MediaMagic.isZip(magic)
        }
        return false
    }
}
