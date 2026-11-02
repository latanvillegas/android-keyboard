package org.futo.inputmethod.latin.uix.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** JVM tests run as part of stableRelease, without creating Debug APKs. */
class ThemeZipSecurityTest {
    private fun archive(vararg files: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun invalidZip(bytes: ByteArray): Boolean = try {
        ThemeZipSecurity.validate(bytes)
        false
    } catch (_: IllegalArgumentException) {
        true
    }

    @Test
    fun acceptsLegacyThemeZipWithValidAsset() {
        val bytes = archive(
            "theme.txt" to "name='Legacy'".toByteArray(),
            "icons/mic.png" to byteArrayOf(1, 2, 3)
        )
        ThemeZipSecurity.validate(bytes)
        assertEquals(bytes.size, ThemeZipSecurity.readBounded(
            ByteArrayInputStream(bytes)
        ).size)
    }

    @Test
    fun rejectsTraversalAndAbsolutePaths() {
        assertTrue(invalidZip(archive(
            "theme.txt" to byteArrayOf(1),
            "../outside.png" to byteArrayOf(2)
        )))
        assertTrue(invalidZip(archive(
            "theme.txt" to byteArrayOf(1),
            "/absolute.png" to byteArrayOf(2)
        )))
    }

    @Test
    fun rejectsInvalidIdsButAcceptsExistingAuroraId() {
        assertTrue(ThemeZipSecurity.validId(
            "io.github.latanvillegas.aurora_prism_hyperglass_3d_v4"
        ))
        assertTrue(ThemeZipSecurity.validId("aurora-2026"))
        assertFalse(ThemeZipSecurity.validId("../themes"))
        assertFalse(ThemeZipSecurity.validId("a/b/c"))
        assertFalse(ThemeZipSecurity.validId("xx"))
    }

    @Test
    fun rejectsArchiveWithoutThemeConfig() {
        assertTrue(invalidZip(archive("images/key.png" to byteArrayOf(1))))
    }

    @Test
    fun rejectsZipBombWithoutMaterializingItInMemory() {
        val payload = ByteArray((ThemeZipSecurity.MAX_ENTRY_BYTES + 4096).toInt())
        val bytes = archive("theme.txt" to byteArrayOf(1), "art/huge.png" to payload)
        assertTrue(invalidZip(bytes))
    }
}
