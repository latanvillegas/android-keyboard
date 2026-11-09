package org.futo.inputmethod.latin.uix.actions.media

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ImageIO is used only in the JVM test, never in the Android runtime.
 * Independently verifies LZW widths and multi-frame GIF89a signatures.
 */
class Gif89aWriterTest {
    @Test fun emitsDecodableMultiFrameGifAtLzwCodeBoundaries() {
        val width = 100
        val height = 100
        val writer = Gif89aWriter(width, height)
        val first = ByteArray(width * height) { (it % 251 + 1).toByte() }
        val second = ByteArray(width * height) {
            if (it % 11 == 0) 0.toByte() else ((it * 17) % 251 + 1).toByte()
        }
        writer.addIndexedFrame(first, 80)
        writer.addIndexedFrame(second, 160)
        val gif = writer.finish()
        assertTrue(gif.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray()))
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(gif)).use { input ->
                reader.input = input
                assertEquals(2, reader.getNumImages(true))
                val image1 = reader.read(0)
                val image2 = reader.read(1)
                assertEquals(width, image1.width)
                assertEquals(height, image1.height)
                // Color palette entries may be remapped during ImageIO decode,
                // but every frame must remain independently readable.
                assertTrue(image1.getRGB(30, 30) != 0)
                assertTrue(image2.getRGB(30, 30) != 0)
            }
        } finally {
            reader.dispose()
        }
    }

    @Test fun outputIsDeterministic() {
        fun generate(): ByteArray {
            val writer = Gif89aWriter(4, 4)
            writer.addIndexedFrame(ByteArray(16) { it.toByte() }, 100)
            writer.addIndexedFrame(ByteArray(16) { (15-it).toByte() }, 120)
            return writer.finish()
        }
        assertTrue(generate().contentEquals(generate()))
    }
}
