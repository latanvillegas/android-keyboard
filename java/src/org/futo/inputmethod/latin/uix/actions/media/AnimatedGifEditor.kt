package org.futo.inputmethod.latin.uix.actions.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Offline frame-accurate GIF editing using Android's GIF Movie decoder.
 * Export is a genuine animated GIF89a with independent transparent frames,
 * loop extension and per-frame delays. Original GIF bytes remain untouched.
 *
 * GIF necessarily quantizes to a 256-entry palette. For original fidelity,
 * keep the unedited source in MediaLibrary.
 */
internal object AnimatedGifEditor {
    private const val MAX_INPUT = 24 * 1024 * 1024
    private const val MAX_OUTPUT_FRAMES = 60
    private const val MAX_DURATION_MS = 15_000
    private const val MAX_OUTPUT_SIDE = 360

    data class Options(
        val startPercent: Int = 0,
        val endPercent: Int = 100,
        val speedPercent: Int = 100,
        val centerCrop: Boolean = false,
        val overlay: String = ""
    ) {
        fun validate() {
            require(startPercent in 0..99)
            require(endPercent in 1..100 && endPercent > startPercent)
            require(speedPercent in 50..200)
            require(overlay.length <= 80)
        }
    }

    private fun readBounded(source: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(32768)
        while (true) {
            val count = source.read(chunk)
            if (count < 0) break
            if (out.size() > MAX_INPUT - count)
                throw IOException("GIF source exceeds 24 MiB")
            out.write(chunk, 0, count)
        }
        return out.toByteArray()
    }

    fun render(context: Context, uri: Uri, options: Options): ByteArray {
        options.validate()
        if (uri.scheme != "content") throw IOException("Select a GIF from file picker")
        val bytes = context.contentResolver.openInputStream(uri)?.use { readBounded(it) }
            ?: throw IOException("Cannot open selected GIF")
        return renderBytes(bytes, options)
    }

    fun renderBytes(bytes: ByteArray, options: Options): ByteArray {
        options.validate()
        if (bytes.size < 12 ||
            !(bytes.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray()) ||
                bytes.copyOfRange(0, 6).contentEquals("GIF87a".toByteArray()))) {
            throw IOException("A genuine animated GIF is required")
        }
        val movie = Movie.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IOException("Android cannot decode this GIF")
        val duration = movie.duration()
        if (duration < 100 || duration > 120_000 || movie.width() < 1 ||
            movie.height() < 1 || movie.width() > 4096 ||
            movie.height() > 4096) {
            throw IOException("Unsupported GIF dimensions or timeline")
        }
        val start = duration.toLong() * options.startPercent / 100
        val end = duration.toLong() * options.endPercent / 100
        val span = end - start
        if (span < 100 || span > MAX_DURATION_MS.toLong())
            throw IOException("Select between 100 ms and 15 seconds")
        val sourceFrameDelay = max(80, ceil(span / MAX_OUTPUT_FRAMES.toDouble()).toInt())
        val frameCount = ceil(span / sourceFrameDelay.toDouble()).toInt().coerceIn(2, MAX_OUTPUT_FRAMES)
        val outputDelay = (sourceFrameDelay.toLong() * 100 / options.speedPercent)
            .toInt().coerceIn(20, 1000)
        val width: Int
        val height: Int
        if (options.centerCrop) {
            width = min(MAX_OUTPUT_SIDE, min(movie.width(), movie.height()))
            height = width
        } else {
            val scale = min(1.0, MAX_OUTPUT_SIDE.toDouble() /
                max(movie.width(), movie.height()))
            width = (movie.width() * scale).toInt().coerceAtLeast(1)
            height = (movie.height() * scale).toInt().coerceAtLeast(1)
        }
        val encoder = Gif89aWriter(width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (i in 0 until frameCount) {
                bitmap.eraseColor(Color.TRANSPARENT)
                val canvas = Canvas(bitmap)
                val timestamp = min(duration - 1,
                    (start + i.toLong() * span / frameCount).toInt())
                movie.setTime(timestamp)
                val fit = if (options.centerCrop) {
                    max(width.toFloat() / movie.width(), height.toFloat() / movie.height())
                } else {
                    min(width.toFloat() / movie.width(), height.toFloat() / movie.height())
                }
                canvas.save()
                canvas.translate(
                    (width - movie.width() * fit) / 2f,
                    (height - movie.height() * fit) / 2f
                )
                canvas.scale(fit, fit)
                movie.draw(canvas, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
                val text = options.overlay.trim()
                if (text.isNotEmpty()) {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                        textSize = (width / 9f).coerceIn(12f, 38f)
                    }
                    while (paint.measureText(text) > width * .93f &&
                        paint.textSize > 10f) {
                        paint.textSize -= 1f
                    }
                    val y = height - paint.textSize * .4f
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = max(2f, width / 120f)
                    paint.color = Color.BLACK
                    canvas.drawText(text, width / 2f, y, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = Color.WHITE
                    canvas.drawText(text, width / 2f, y, paint)
                }
                encoder.addFrame(bitmap, outputDelay)
            }
        } finally {
            bitmap.recycle()
        }
        val output = encoder.finish()
        if (output.size > MediaLibrary.MAX_ITEM_BYTES)
            throw IOException("Edited GIF exceeds library's 24 MiB quota")
        return output
    }
}

/**
 * Minimal standards-conformant GIF89a encoder with a fixed 3-3-2 palette
 * (index 0 reserved for transparency) and 12-bit GIF LZW.
 *
 * No native dependencies, network access, or nondeterministic encoders.
 */
internal class Gif89aWriter(private val width: Int, private val height: Int) {
    private val stream = ByteArrayOutputStream()
    private var frameCount = 0
    private var finished = false

    init {
        require(width in 1..2048 && height in 1..2048)
        stream.write("GIF89a".toByteArray(Charsets.US_ASCII))
        word(width)
        word(height)
        stream.write(byteArrayOf(0xf7.toByte(), 0, 0))
        for (index in 0..255) {
            if (index == 0) {
                stream.write(byteArrayOf(0, 0, 0))
            } else {
                val sample = if (index == 1) 0 else index
                stream.write(byteArrayOf(
                    (((sample ushr 5) and 7) * 255 / 7).toByte(),
                    (((sample ushr 2) and 7) * 255 / 7).toByte(),
                    ((sample and 3) * 255 / 3).toByte()
                ))
            }
        }
        // Netscape loop extension: repeat indefinitely.
        stream.write(byteArrayOf(
            0x21, 0xff.toByte(), 11, 0x4e, 0x45, 0x54, 0x53, 0x43, 0x41,
            0x50, 0x45, 0x32, 0x2e, 0x30, 3, 1, 0, 0, 0
        ))
    }

    private fun word(value: Int) {
        stream.write(value and 0xff)
        stream.write((value ushr 8) and 0xff)
    }

    fun addFrame(bitmap: Bitmap, delayMs: Int) {
        check(!finished) { "GIF writer already finished" }
        require(bitmap.width == width && bitmap.height == height)
        require(delayMs in 20..1000)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val indexes = ByteArray(pixels.size)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val alpha = pixel ushr 24
            if (alpha < 128) {
                indexes[i] = 0
            } else {
                val red = (pixel ushr 16) and 0xff
                val green = (pixel ushr 8) and 0xff
                val blue = pixel and 0xff
                val quantized = (red and 0xe0) or
                    ((green and 0xe0) ushr 3) or (blue ushr 6)
                indexes[i] = max(1, quantized).toByte()
            }
        }
        addIndexedFrame(indexes, delayMs)
    }

    /** Indexed route is deliberately pure JVM-testable. Index 0 is transparent. */
    internal fun addIndexedFrame(indexes: ByteArray, delayMs: Int) {
        check(!finished)
        require(indexes.size == width * height)
        require(delayMs in 20..1000)
        // Disposal method 2 clears the frame to transparent background.
        stream.write(byteArrayOf(0x21, 0xf9.toByte(), 4, 0x09))
        word(max(2, (delayMs + 5) / 10))
        stream.write(byteArrayOf(0, 0))
        stream.write(0x2c)
        word(0)
        word(0)
        word(width)
        word(height)
        stream.write(0)
        stream.write(8)
        val encoded = lzw(indexes)
        for (start in encoded.indices step 255) {
            val count = min(255, encoded.size - start)
            stream.write(count)
            stream.write(encoded, start, count)
        }
        stream.write(0)
        frameCount++
    }

    private fun lzw(source: ByteArray): ByteArray {
        require(source.isNotEmpty())
        val out = ByteArrayOutputStream()
        var codeSize = 9
        var nextCode = 258
        var bits = 0
        var bitCount = 0
        val dict = HashMap<Int, Int>(4096)
        fun emit(code: Int) {
            bits = bits or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                out.write(bits and 0xff)
                bits = bits ushr 8
                bitCount -= 8
            }
        }
        emit(256) // clear table
        var previous = source[0].toInt() and 0xff
        for (i in 1 until source.size) {
            val current = source[i].toInt() and 0xff
            val pair = (previous shl 8) or current
            val found = dict[pair]
            if (found != null) {
                previous = found
            } else {
                emit(previous)
                if (nextCode < 4096) {
                    dict[pair] = nextCode++
                    // GIF decoder installs dictionary entries one code later.
                    if (nextCode == (1 shl codeSize) + 1 && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    emit(256)
                    dict.clear()
                    nextCode = 258
                    codeSize = 9
                }
                previous = current
            }
        }
        emit(previous)
        emit(257) // end of information
        if (bitCount > 0) out.write(bits and 0xff)
        return out.toByteArray()
    }

    fun finish(): ByteArray {
        check(!finished && frameCount >= 2) { "At least 2 frames required" }
        finished = true
        stream.write(0x3b)
        return stream.toByteArray()
    }
}
