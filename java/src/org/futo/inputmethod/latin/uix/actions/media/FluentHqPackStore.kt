package org.futo.inputmethod.latin.uix.actions.media

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.uix.actions.emoji.FluentEmoji3D
import org.futo.inputmethod.latin.uix.actions.emoji.FluentEmoji3DMotion
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
    private const val ROOT = "fluent-full-v2"
    private const val COMPLETE = ".installed"
    private const val MAX_PACK_MEMBERS = 6500
    private const val MAX_PACK_BYTES = 224L * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
    private val keyPattern = Regex("[0-9a-f]+(?:-[0-9a-f]+)*")
    private val names = Regex(
        "(?:emoji3d|emoji3d-share|emoji3d-motion)/" +
            "(?:[0-9a-f]+(?:-[0-9a-f]+)*\\.(?:webp|gif)|index\\.txt|LICENSE\\.txt|SOURCE\\.json)"
    )
    private val lock = Any()

    private fun base(context: Context): File =
        File(context.applicationContext.noBackupFilesDir, "fluent-hq")

    private fun current(context: Context): File = File(base(context), ROOT)
    fun isInstalled(context: Context): Boolean =
        File(current(context), COMPLETE).isFile

    fun contentVersion(context: Context): String = try {
        File(current(context), COMPLETE).takeIf { it.isFile }
            ?.readText(Charsets.UTF_8) ?: "no-pack"
    } catch (_: IOException) {
        "no-pack"
    }

    fun keys(context: Context, folder: String): Set<String>? {
        if (folder != "emoji3d" && folder != "emoji3d-share" &&
            folder != "emoji3d-motion") return null
        if (!isInstalled(context)) return null
        return try {
            File(File(current(context), folder), "index.txt")
                .useLines(Charsets.UTF_8) { lines ->
                    lines.filter { validKey -> keyPattern.matches(validKey) }.toHashSet()
                }
        } catch (_: IOException) {
            null
        }
    }

    private fun open(
        context: Context, folder: String, key: String, extension: String
    ): InputStream? {
        if (!keyPattern.matches(key) || !isInstalled(context)) return null
        val source = File(current(context), folder + "/" + key + "." + extension)
        return try {
            if (source.isFile && source.length() in 1L..MAX_ENTRY_BYTES)
                source.inputStream() else null
        } catch (_: IOException) {
            null
        }
    }

    fun openPicker(context: Context, key: String): InputStream? =
        open(context, "emoji3d", key, "webp")

    fun openShare(context: Context, key: String): InputStream? =
        open(context, "emoji3d-share", key, "webp")

    fun openMotion(context: Context, key: String): InputStream? =
        open(context, "emoji3d-motion", key, "webp")

    fun openGif(context: Context, key: String): InputStream? =
        open(context, "emoji3d-motion", key, "gif")

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
        if (json.optString("format") != "fluent3d-full-v2" ||
            json.optInt("version") != 2 ||
            json.optInt("ui_count") !in 2500..3500 ||
            json.optInt("share_count") != json.optInt("ui_count") ||
            json.optInt("motion_count") != 30) {
            throw IOException("Unsupported Fluent pack format")
        }
        return json
    }

    private fun validateCatalog(root: File, folderName: String, expected: Int) {
        val folder = File(root, folderName)
        val keys = File(folder, "index.txt").readLines(Charsets.UTF_8)
        if (keys.size != expected || keys.toSet().size != expected ||
            keys.any { !keyPattern.matches(it) }) {
            throw IOException("Invalid Fluent catalog: $folderName")
        }
        if (!File(folder, "LICENSE.txt").isFile ||
            !File(folder, "SOURCE.json").isFile) {
            throw IOException("Missing Fluent attribution")
        }
        val formats = if (folderName == "emoji3d-motion")
            listOf("webp", "gif") else listOf("webp")
        for (key in keys) for (format in formats) {
            val file = File(folder, "$key.$format")
            if (!file.isFile || file.length() !in 1L..MAX_ENTRY_BYTES)
                throw IOException("Missing Fluent art: $key.$format")
            val header = ByteArray(12)
            val filled = file.inputStream().use { source ->
                var position = 0
                while (position < 12) {
                    val count = source.read(header, position, 12 - position)
                    if (count < 0) break
                    position += count
                }
                position
            }
            if (filled != 12) throw IOException("Truncated Fluent artwork")
            val valid = if (format == "webp") {
                String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                    String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
            } else {
                val magic = String(header, 0, 6, Charsets.US_ASCII)
                magic == "GIF89a" || magic == "GIF87a"
            }
            if (!valid) throw IOException("Invalid Fluent artwork header")
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
                        val expectedCount = 2 * manifest.getInt("ui_count") +
                            2 * manifest.getInt("motion_count") + 9
                        if (expectedCount > MAX_PACK_MEMBERS || files.length() != expectedCount)
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
                            stage, "emoji3d", manifest.getInt("ui_count")
                        )
                        validateCatalog(
                            stage, "emoji3d-share", manifest.getInt("share_count")
                        )
                        validateCatalog(
                            stage, "emoji3d-motion", manifest.getInt("motion_count")
                        )
                        if (!File(stage, "emoji3d/index.txt").readBytes().contentEquals(
                                File(stage, "emoji3d-share/index.txt").readBytes())) {
                            throw IOException("Fluent UI and sharing indexes differ")
                        }
                    }
                }
                File(stage, COMPLETE).writeText(
                    System.currentTimeMillis().toString() + "-" + System.nanoTime(),
                    Charsets.UTF_8
                )
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
                // A previously installed v1 pack lacks picker/GIF assets.
                // Clean it only after successfully activating the complete v2.
                File(parent, "fluent-hq-v1").deleteRecursively()
                FluentEmoji3D.invalidateResources()
                FluentEmoji3DMotion.invalidateResources()
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
            if (removed) {
                FluentEmoji3D.invalidateResources()
                FluentEmoji3DMotion.invalidateResources()
                MediaLibrary.changes.tryEmit(Unit)
            }
            removed
        }
    }
}
