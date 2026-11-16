package org.futo.inputmethod.latin.uix.actions.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Video-to-GIF (SAF input, system MediaMetadataRetriever decoder).
 *
 * No FFmpeg or bundled native library. Quantization reuses the bounded GIF89a
 * encoder already employed by FUTO; 320px/60-frame/15s/24-MiB limits keep
 * memory use manageable for an IME host.
 */
class VideoGifCreatorActivity : ComponentActivity() {
    private var videoUri: Uri? = null
    private lateinit var status: TextView
    private lateinit var startSeconds: EditText
    private lateinit var durationSeconds: EditText
    private lateinit var fps: SeekBar
    private lateinit var exportButton: Button

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        videoUri = uri
        try {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
            // A transient SAF grant is sufficient while the activity is alive.
        }
        status.setText(R.string.media_video_ready)
        exportButton.isEnabled = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoUri = savedInstanceState?.getString("video_uri")?.let(Uri::parse)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        panel.addView(TextView(this).apply {
            setText(R.string.media_video_gif_title)
            textSize = 23f
        })
        panel.addView(TextView(this).apply {
            setText(R.string.media_video_gif_limits)
            textSize = 14f
        })
        panel.addView(Button(this).apply {
            setText(R.string.media_video_choose)
            setOnClickListener { picker.launch(arrayOf("video/*")) }
        })
        fun number(label: Int, initial: String): EditText {
            panel.addView(TextView(this).apply {
                setText(label)
                textSize = 16f
            })
            val input = EditText(this).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                setText(initial)
            }
            panel.addView(input)
            return input
        }
        startSeconds = number(R.string.media_video_start, "0")
        durationSeconds = number(R.string.media_video_duration, "5")
        panel.addView(TextView(this).apply {
            setText(R.string.media_video_fps)
            textSize = 16f
        })
        fps = SeekBar(this).apply { max = 6; progress = 2 }
        panel.addView(fps)
        status = TextView(this).apply {
            setText(if (videoUri == null) R.string.media_video_choose
                else R.string.media_video_ready)
            textSize = 15f
        }
        panel.addView(status)
        exportButton = Button(this).apply {
            setText(R.string.media_video_create)
            isEnabled = videoUri != null
            setOnClickListener { createGif() }
        }
        panel.addView(exportButton)
        setContentView(panel)
        if (savedInstanceState == null) picker.launch(arrayOf("video/*"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("video_uri", videoUri?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun createGif() {
        val uri = videoUri ?: return
        val from = startSeconds.text.toString().toDoubleOrNull()
        val seconds = durationSeconds.text.toString().toDoubleOrNull()
        if (from == null || seconds == null || from < 0 || seconds !in 0.1..15.0) {
            status.setText(R.string.media_video_invalid)
            return
        }
        exportButton.isEnabled = false
        val framesPerSecond = fps.progress + 4
        lifecycleScope.launch {
            try {
                val generated = withContext(Dispatchers.IO) {
                    render(uri, (from * 1000).toLong(), (seconds * 1000).toLong(), framesPerSecond) {
                        index, total ->
                        runOnUiThread {
                            status.text = getString(R.string.media_video_progress, index, total)
                        }
                    }
                }
                val saved = withContext(Dispatchers.IO) {
                    MediaLibrary.importStream(
                        this@VideoGifCreatorActivity,
                        ByteArrayInputStream(generated),
                        "video-gif-" + System.currentTimeMillis() + ".gif"
                    )
                }
                Toast.makeText(this@VideoGifCreatorActivity,
                    getString(R.string.gif_editor_saved, saved.displayName),
                    Toast.LENGTH_LONG).show()
                finish()
            } catch (error: Exception) {
                status.text = error.localizedMessage ?: getString(R.string.media_video_invalid)
                exportButton.isEnabled = true
            }
        }
    }

    private suspend fun render(
        uri: Uri,
        offsetMs: Long,
        lengthMs: Long,
        fps: Int,
        onProgress: (Int, Int) -> Unit
    ): ByteArray {
        require(uri.scheme == "content")
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val totalMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: throw IOException("Video has no duration")
            val width = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
            )?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
            )?.toIntOrNull() ?: 0
            if (totalMs <= 100 || (offsetMs < 0L || offsetMs >= totalMs) ||
                lengthMs !in 100L..15000L || fps !in 4..10 ||
                width !in 1..4096 || height !in 1..4096 ||
                (Build.VERSION.SDK_INT < 27 && (width > 1920 || height > 1920))) {
                throw IOException("Unsupported source dimensions, time range or frame rate")
            }
            val spanMs = minOf(lengthMs, totalMs - offsetMs)
            if (spanMs < 100) throw IOException("Selected video interval too short")
            val rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
            )?.toIntOrNull() ?: 0
            val w = if (rotation == 90 || rotation == 270) height else width
            val h = if (rotation == 90 || rotation == 270) width else height
            val scale = minOf(1.0, 320.0 / maxOf(w, h).toDouble())
            val outputWidth = (w * scale).roundToInt().coerceIn(1, 320)
            val outputHeight = (h * scale).roundToInt().coerceIn(1, 320)
            val frames = ceil(spanMs * fps / 1000.0).toInt().coerceIn(2, 60)
            val delayMs = (1000.0 / fps).roundToInt().coerceIn(20, 1000)
            val writer = Gif89aWriter(outputWidth, outputHeight)

            repeat(frames) { index ->
                currentCoroutineContext().ensureActive()
                val timeUs = (offsetMs + index.toLong() * spanMs / frames) * 1000L
                val frame = if (Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(
                        timeUs, MediaMetadataRetriever.OPTION_CLOSEST,
                        outputWidth, outputHeight
                    )
                } else {
                    retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                } ?: throw IOException("Unable to decode video frame " + (index + 1))
                try {
                    val scaled = if (frame.width == outputWidth && frame.height == outputHeight) {
                        frame
                    } else {
                        Bitmap.createScaledBitmap(frame, outputWidth, outputHeight, true)
                    }
                    try {
                        writer.addFrame(scaled, delayMs)
                    } finally {
                        if (scaled !== frame) scaled.recycle()
                    }
                } finally {
                    frame.recycle()
                }
                onProgress(index + 1, frames)
            }
            return writer.finish().also {
                if (it.size > MediaLibrary.MAX_ITEM_BYTES) {
                    throw IOException("GIF exceeds offline library's 24 MiB limit")
                }
            }
        } finally {
            retriever.release()
        }
    }
}
