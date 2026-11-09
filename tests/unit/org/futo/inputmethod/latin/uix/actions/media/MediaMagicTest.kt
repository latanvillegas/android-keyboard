package org.futo.inputmethod.latin.uix.actions.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM regression tests for signature validation. */
class MediaMagicTest {
    @Test fun recognizesGenuineFormats() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 0
        )
        val gif = "GIF89a".toByteArray() + ByteArray(8)
        val webp = "RIFF".toByteArray() + byteArrayOf(4, 0, 0, 0) +
            "WEBP".toByteArray()
        val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(),
            0xff.toByte()) + ByteArray(9)
        assertEquals(MediaKind.PNG, MediaMagic.detect(png))
        assertEquals(MediaKind.GIF, MediaMagic.detect(gif))
        assertEquals(MediaKind.WEBP, MediaMagic.detect(webp))
        assertEquals(MediaKind.JPEG, MediaMagic.detect(jpeg))
    }

    @Test fun rejectsMismatchedOrTruncatedFiles() {
        assertNull(MediaMagic.detect("fake.png".toByteArray()))
        assertNull(MediaMagic.detect("RIFFxxxxxxxxJPEG".toByteArray()))
        assertNull(MediaMagic.detect(ByteArray(8)))
        assertFalse(MediaMagic.isZip("PNG ".toByteArray()))
        assertTrue(MediaMagic.isZip(byteArrayOf(0x50, 0x4b, 3, 4)))
    }
}
