package org.futo.inputmethod.latin.uix.theme

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Bounded validation of untrusted theme archives.
 *
 * Rejects directory traversal, duplicate paths and ZIP bombs before the
 * ordinary FUTO theme reader opens image/font assets. Existing V1 packages
 * and the optional V2 properties use the same interoperable ZIP container.
 */
internal object ThemeZipSecurity {
    const val MAX_COMPRESSED_BYTES = 48L * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES = 160L * 1024 * 1024
    const val MAX_ENTRY_BYTES = 16L * 1024 * 1024
    const val MAX_CONFIG_BYTES = 2L * 1024 * 1024
    const val MAX_ENTRIES = 512

    fun validId(id: String): Boolean =
        id.length in 3..100 &&
            id != "." && id != ".." &&
            !id.contains('/') && !id.contains('\\') &&
            !id.contains("..") &&
            id.none { it.code < 32 }

    private fun validEntryName(name: String): Boolean {
        if (name.isBlank() || name.length > 255 || name.startsWith('/') ||
            name.contains('\\') || name.any { it.code < 32 }) return false
        val parts = name.trimEnd('/').split('/')
        return parts.size <= 8 && parts.all { it.isNotEmpty() && it != "." && it != ".." }
    }

    /** Reads at most 48 MiB without trusting Content-Length or entry headers. */
    fun readBounded(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val length = input.read(buffer)
            if (length < 0) break
            total += length
            require(total <= MAX_COMPRESSED_BYTES) { "El tema ZIP supera el límite de 48 MiB." }
            out.write(buffer, 0, length)
        }
        return out.toByteArray()
    }

    /** Streams each inflated entry; never trusts spoofed ZIP metadata. */
    fun validate(bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size.toLong() <= MAX_COMPRESSED_BYTES) {
            "El tema ZIP es demasiado grande."
        }
        var entryCount = 0
        var inflatedTotal = 0L
        var hasConfig = false
        val seen = HashSet<String>()
        val buffer = ByteArray(16 * 1024)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                require(entryCount <= MAX_ENTRIES) { "El tema contiene demasiados archivos." }
                require(validEntryName(entry.name) && seen.add(entry.name)) {
                    "El ZIP contiene una ruta insegura o duplicada."
                }
                if (!entry.isDirectory) {
                    var entryTotal = 0L
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        entryTotal += n
                        inflatedTotal += n
                        val cap = if (entry.name == "theme.txt" || entry.name == "config.json") {
                            MAX_CONFIG_BYTES
                        } else MAX_ENTRY_BYTES
                        require(entryTotal <= cap && inflatedTotal <= MAX_UNCOMPRESSED_BYTES) {
                            "El tema contiene un archivo o datos descomprimidos demasiado grandes."
                        }
                    }
                    if (entry.name == "theme.txt" || entry.name == "config.json") {
                        hasConfig = true
                    }
                }
                zip.closeEntry()
            }
        }
        require(entryCount > 0 && hasConfig) { "Falta theme.txt o config.json." }
    }

    /**
     * Validate before replacing an installed theme. Same-directory rename
     * preserves the previous working theme if the new archive is invalid.
     */
    fun install(input: InputStream, directory: File, id: String): File {
        require(validId(id)) { "El identificador del tema contiene caracteres inseguros." }
        val bytes = readBounded(input)
        validate(bytes)
        val temp = File.createTempFile(".theme-", ".tmp", directory)
        try {
            temp.outputStream().use { it.write(bytes) }
            val destination = File(directory, "$id.zip")
            check(temp.renameTo(destination)) { "No se pudo guardar el tema ZIP." }
            return destination
        } finally {
            temp.delete()
        }
    }
}
