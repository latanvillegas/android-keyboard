package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Portable, lossless backup: source media bytes are copied without transcoding. */
internal object MediaPackExporter {
    suspend fun export(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") throw IOException("Only SAF output URIs supported")
        val items = MediaLibrary.list(context)
        if (items.isEmpty()) throw IOException("No media to export")
        val out = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IOException("Cannot write selected archive")
        ZipOutputStream(BufferedOutputStream(out)).use { archive ->
            val manifest = JSONObject().apply {
                put("format", "futo-media-pack")
                put("version", 1)
                put("items", JSONArray().apply {
                    for (item in items) {
                        put(JSONObject().apply {
                            put("path", "media/" + item.id + "." + item.kind.extension)
                            put("name", item.displayName)
                            put("sha256", item.id)
                            put("favorite", item.favorite)
                            put("mime", item.kind.mime)
                        })
                    }
                })
            }
            archive.putNextEntry(ZipEntry("pack.json"))
            archive.write(manifest.toString().toByteArray(Charsets.UTF_8))
            archive.closeEntry()
            for (item in items) {
                val file = MediaLibrary.fileFor(context, item)
                if (!file.isFile) throw IOException("Media disappeared during export")
                archive.putNextEntry(ZipEntry(
                    "media/" + item.id + "." + item.kind.extension
                ))
                file.inputStream().buffered().use { it.copyTo(archive, 32 * 1024) }
                archive.closeEntry()
            }
        }
        items.size
    }
}
