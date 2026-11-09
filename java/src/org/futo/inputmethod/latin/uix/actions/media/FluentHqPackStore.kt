package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Installable high-resolution Fluent art. Runs entirely offline; no code,
 * native libraries or arbitrary filesystem paths may be installed.
 *
 * Pack installation is independent of the existing emoji picker and the
 * selected APK distribution. The base APK keeps its GIF fallbacks.
 */
internal object FluentHqPackStore {
    private const val ROOT = "fluent-hq-v1"
    private const val COMPLETE = ".installed"
    private const val MAX_PACK_MEMBERS = 3200
    private const val MAX_PACK_BYTES = 192L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 512 * 1024
    private val keyPattern = Regex("[0-9a-f]+(?:-[0-9a-f]+)*")
    private val names = Regex(
        "(?:emoji3d-share|emoji3d-motion)/(?:[0-9a-f]+(?:-[0-9a-f]+)*\\.webp|index\\.txt|LICENSE\\.txt|SOURCE\\.json)"
    )
    private val lock = Any()

    private fun base(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, "fluent-hq")

    private fun current(context: Context): File = File(base(context), ROOT)
    fun isInstalled(context: Context): Boolean =
        File(current(context), COMPLETE).isFile

    private fun open(context: Context, folder: String, key: String): InputStream? {
        if (!keyPattern.matches(key) || !isInstalled(context)) return null
        val source = File(current(context), folder + "/" + key + ".webp")
        return if (source.isFile && source.length() in 1..MAX_ENTRY_BYTES)
            source.inputStream()
        else null
    }

    fun openShare(context: Context, key: String): InputStream? =
        open(context, "emoji3d-share", key)

    fun openMotion(context: Context, key: String): InputStream? =
        open(context, "emoji3d-motion", key)

    /** Optional pack recognition without reading the entire archive. */
    fun isFluentPack(context: Context, uri: Uri): Boolean {
        if (uri.scheme != "content") return false
        return try {
            context.contentResolver.openInputStream(uri)?.use { source ->
                ZipInputStream(source.buffered()).use { archive ->
                    archive.nextEntry?.name == "fluent-hq.json"
                }
            } == true
        } catch (_: IOException) {
            false
        }
    }

    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val bytes = ByteArray(32 * 1024)
            while (true) {
                val n = stream.read(bytes)
                if (n < 0) break
                digest.update(bytes, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun readManifest(archive: ZipInputStream): JSONObject {
        var total = 0
        val result = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = archive.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_MANIFEST_BYTES)
                throw IOException("Fluent pack manifest too large")
            result.write(buffer, 0, count)
        }
        val json = JSONObject(result.toString("UTF-8"))
        if (json.optString("format") != "fluent3d-hq-v1" ||
            json.optInt("version") != 1 ||
            json.optInt("static_count") < 2500 ||
            json.optInt("motion_count") != 30) {
            throw IOException("Unsupported Fluent pack format")
        }
        return json
    }

    private fun validateCatalog(folder: File, expected: Int) {
        val keys = File(folder, "index.txt").readLines(Charsets.UTF_8)
        if (keys.size != expected || keys.toSet().size != expected ||
            keys.any { !keyPattern.matches(it) }) {
            throw IOException("Invalid Fluent artwork index")
        }
        if (!File(folder, "LICENSE.txt").isFile ||
            !File(folder, "SOURCE.json").isFile) {
            throw IOException("Fluent source metadata is missing")
        }
        for (key in keys) {
            val file = File(folder, key + ".webp")
            if (!file.isFile || file.length() !in 1..MAX_ENTRY_BYTES) {
                throw IOException("Missing Fluent artwork: " + key)
            }
            val header = ByteArray(12)
            FileInputStream(file).use { input ->
                var offset = 0
                while (offset < header.size) {
                    val n = input.read(header, offset, header.size - offset)
                    if (n < 0) break
                    offset += n
                }
            }
            if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                String(header, 8, 4, Charsets.US_ASCII) != "WEBP")
                throw IOException("Invalid Fluent WebP: " + key)
        }
    }

    suspend fun install(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
        if (uri.scheme != "content") throw IOException("Only SAF imports allowed")
        synchronized(lock) {
            val parent = base(context)
            if (!parent.exists() && !parent.mkdirs())
                throw IOException("Unable to create Fluent pack directory")
            val stage = File(parent, ROOT + "-stage-" + System.nanoTime())
            if (!stage.mkdirs()) throw IOException("Unable to stage Fluent pack")
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Cannot open Fluent pack")
                val seen = HashSet<String>(3100)
                var totalBytes = 0L
                input.use { source ->
                    ZipInputStream(source.buffered()).use { archive ->
                        val first = archive.nextEntry
                            ?: throw IOException("Fluent pack is empty")
                        if (first.name != "fluent-hq.json")
                            throw IOException("Invalid Fluent pack manifest location")
                        val manifest = readManifest(archive)
                        archive.closeEntry()
                        val files = manifest.getJSONObject("members")
                        if (files.length() !in 2500..MAX_PACK_MEMBERS)
                            throw IOException("Invalid Fluent pack inventory")

                        var count = 0
                        while (true) {
                            val entry = archive.nextEntry ?: break
                            count++
                            if (count > MAX_PACK_MEMBERS ||
                                entry.isDirectory || !names.matches(entry.name) ||
                                !files.has(entry.name) || !seen.add(entry.name)) {
                                throw IOException("Unexpected or duplicate Fluent pack member")
                            }
                            if (entry.size > MAX_ENTRY_BYTES)
                                throw IOException("Oversized Fluent image member")
                            val output = File(stage, entry.name)
                            if (!output.parentFile.isDirectory && !output.parentFile.mkdirs())
                                throw IOException("Unable to create Fluent pack folder")
                            val digest = MessageDigest.getInstance("SHA-256")
                            var memberBytes = 0L
                            output.outputStream().buffered().use { target ->
                                val buffer = ByteArray(32 * 1024)
                                while (true) {
                                    val n = archive.read(buffer)
                                    if (n < 0) break
                                    memberBytes += n
                                    totalBytes += n
                                    if (memberBytes > MAX_ENTRY_BYTES || totalBytes > MAX_PACK_BYTES)
                                        throw IOException("Fluent pack size limit exceeded")
                                    target.write(buffer, 0, n)
                                    digest.update(buffer, 0, n)
                                }
                            }
                            val actual = digest.digest().joinToString("") {
                                "%02x".format(it.toInt() and 0xff)
                            }
                            if (actual != files.getString(entry.name))
                                throw IOException("Fluent pack SHA-256 mismatch")
                            archive.closeEntry()
                        }
                        if (seen.size != files.length())
                            throw IOException("Fluent pack incomplete")
                        validateCatalog(
                            File(stage, "emoji3d-share"),
                            manifest.getInt("static_count")
                        )
                        validateCatalog(
                            File(stage, "emoji3d-motion"),
                            manifest.getInt("motion_count")
                        )
                    }
                }
                File(stage, COMPLETE).writeText("installed")
                val dest = current(context)
                val backup = File(parent, ROOT + "-backup")
                if (backup.exists()) backup.deleteRecursively()
                if (dest.exists() && !dest.renameTo(backup))
                    throw IOException("Unable to swap Fluent pack")
                if (!stage.renameTo(dest)) {
                    if (backup.exists()) backup.renameTo(dest)
                    throw IOException("Unable to publish Fluent pack")
                }
                backup.deleteRecursively()
                MediaLibrary.changes.tryEmit(Unit)
                1
            } finally {
                if (stage.exists()) stage.deleteRecursively()
            }
        }
    }

    suspend fun uninstall(context: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val installed = current(context)
            val removed = installed.isDirectory && installed.deleteRecursively()
            if (removed) MediaLibrary.changes.tryEmit(Unit)
            removed
        }
    }
}
