package org.futo.inputmethod.latin.uix.actions.media

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure Kotlin GIF reader in JVM tests, independent of Android and java.desktop.
 * Checks actual LZW pixel indices, palette tables, block framing and timing.
 */
class Gif89aWriterTest {
    private class Cursor(private val data: ByteArray) {
        var offset = 0
        fun byte(): Int {
            if (offset >= data.size) error("Truncated GIF")
            return data[offset++].toInt() and 0xff
        }
        fun word(): Int = byte() or (byte() shl 8)
        fun skip(length: Int) {
            if (length < 0 || offset + length > data.size) error("GIF overrun")
            offset += length
        }
        fun take(length: Int): ByteArray {
            if (length < 0 || offset + length > data.size) error("GIF overrun")
            val bytes = data.copyOfRange(offset, offset + length)
            offset += length
            return bytes
        }
        fun blocks(): ByteArray {
            val result = ByteArrayOutputStream()
            while (true) {
                val size = byte()
                if (size == 0) return result.toByteArray()
                result.write(take(size))
            }
        }
    }

    private fun decompress(encoded: ByteArray, minimum: Int, pixelCount: Int): ByteArray {
        require(minimum in 2..8)
        val clear = 1 shl minimum
        val stop = clear + 1
        val dictionary = arrayOfNulls<ByteArray>(4096)
        var width = minimum + 1
        var next = clear + 2
        var bitOffset = 0

        fun reset() {
            dictionary.fill(null)
            for (index in 0 until clear) dictionary[index] = byteArrayOf(index.toByte())
            width = minimum + 1
            next = clear + 2
        }

        fun code(): Int {
            if (bitOffset + width > encoded.size * 8) error("Truncated GIF LZW data")
            var result = 0
            for (shift in 0 until width) {
                val position = bitOffset + shift
                val current = encoded[position ushr 3].toInt() and 0xff
                result = result or (((current ushr (position and 7)) and 1) shl shift)
            }
            bitOffset += width
            return result
        }

        reset()
        val out = ByteArrayOutputStream(pixelCount)
        var previous: ByteArray? = null
        while (true) {
            val current = code()
            if (current == clear) {
                reset()
                previous = null
                continue
            }
            if (current == stop) break
            val entry = if (current < next && dictionary[current] != null) {
                requireNotNull(dictionary[current])
            } else if (current == next && previous != null) {
                previous + byteArrayOf(previous[0])
            } else {
                error("Unknown GIF LZW code $current")
            }
            out.write(entry)
            if (out.size() > pixelCount) error("Frame is larger than GIF dimensions")
            if (previous != null && next < 4096) {
                dictionary[next++] = previous + byteArrayOf(entry[0])
                if (next == (1 shl width) && width < 12) width++
            }
            previous = entry
        }
        val pixels = out.toByteArray()
        assertEquals(pixelCount, pixels.size)
        return pixels
    }

    private fun frames(bytes: ByteArray): List<ByteArray> {
        val cursor = Cursor(bytes)
        assertArrayEquals("GIF89a".toByteArray(), cursor.take(6))
        val width = cursor.word()
        val height = cursor.word()
        val global = cursor.byte()
        cursor.skip(2) // background index and pixel aspect ratio
        if ((global and 0x80) != 0) {
            cursor.skip(3 * (1 shl ((global and 7) + 1)))
        }
        val frames = mutableListOf<ByteArray>()
        while (true) {
            when (val marker = cursor.byte()) {
                0x21 -> {
                    cursor.byte() // extension label
                    cursor.blocks()
                }
                0x2c -> {
                    cursor.word() // offset x
                    cursor.word() // offset y
                    val frameWidth = cursor.word()
                    val frameHeight = cursor.word()
                    assertEquals(width, frameWidth)
                    assertEquals(height, frameHeight)
                    val flags = cursor.byte()
                    if (flags and 0x80 != 0) {
                        cursor.skip(3 * (1 shl ((flags and 7) + 1)))
                    }
                    val minimum = cursor.byte()
                    frames += decompress(
                        cursor.blocks(), minimum, frameWidth * frameHeight
                    )
                }
                0x3b -> return frames
                else -> error("Unexpected GIF marker $marker")
            }
        }
    }

    @Test fun emitsDecodableMultiFrameGifAtLzwCodeBoundaries() {
        val width = 100
        val height = 100
        val first = ByteArray(width * height) { ((it % 251) + 1).toByte() }
        val second = ByteArray(width * height) {
            if (it % 11 == 0) 0.toByte() else (((it * 17) % 251) + 1).toByte()
        }
        val writer = Gif89aWriter(width, height)
        writer.addIndexedFrame(first, 80)
        writer.addIndexedFrame(second, 160)
        val result = writer.finish()
        assertTrue(result.size > 900)
        val decoded = frames(result)
        assertEquals(2, decoded.size)
        assertArrayEquals(first, decoded[0])
        assertArrayEquals(second, decoded[1])
    }

    @Test fun repeatedEncodingIsByteIdentical() {
        fun encode(): ByteArray {
            val writer = Gif89aWriter(4, 4)
            writer.addIndexedFrame(ByteArray(16) { it.toByte() }, 100)
            writer.addIndexedFrame(ByteArray(16) { (15 - it).toByte() }, 120)
            return writer.finish()
        }
        assertArrayEquals(encode(), encode())
        assertEquals(2, frames(encode()).size)
    }
}
