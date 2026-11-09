package org.futo.inputmethod.latin.uix.actions.media

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * GIF timeline editor. Modifies a new copy, preserving imported original bytes.
 * Time trim, 50–200% playback, center-crop and animated text overlays all apply
 * to genuine GIF89a output (no misleading static first-frame export).
 */
class GifEditorActivity : ComponentActivity() {
    private var source: Uri? = null
    private lateinit var save: Button
    private lateinit var preview: ImageView
    private lateinit var caption: EditText
    private lateinit var start: SeekBar
    private lateinit var end: SeekBar
    private lateinit var speed: SeekBar
    private lateinit var crop: CheckBox
    private lateinit var status: TextView

    private val pick = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            if (source == null) finish()
            return@registerForActivityResult
        }
        source = uri
        try {
            contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Transient SAF grant is sufficient for this Activity.
        }
        showSelected(uri)
    }

    private fun showSelected(uri: Uri) {
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeStream(stream, null, options)
                    if (options.outWidth !in 1..4096 || options.outHeight !in 1..4096)
                        throw IOException("Unsupported GIF dimensions")
                    val scale = generateSequence(1) { it * 2 }
                        .first { options.outWidth / it <= 480 &&
                            options.outHeight / it <= 480 }
                    contentResolver.openInputStream(uri)?.use { again ->
                        BitmapFactory.decodeStream(again, null,
                            BitmapFactory.Options().apply { inSampleSize = scale })
                    }
                }
            }
                if (bitmap == null) {
                    source = null
                    save.isEnabled = false
                    status.setText(R.string.gif_editor_invalid)
                } else {
                    preview.setImageBitmap(bitmap)
                    save.isEnabled = true
                    status.setText(R.string.gif_editor_tip)
                }
            } catch (error: Exception) {
                source = null
                save.isEnabled = false
                status.text = error.message ?: getString(R.string.gif_editor_invalid)
            }
        }
    }

    private fun title(text: Int): TextView = TextView(this).apply {
        setText(text)
        textSize = 15f
        setPadding(0, 8, 0, 2)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        root.addView(title(R.string.gif_editor_title).apply { textSize = 22f })
        root.addView(Button(this).apply {
            setText(R.string.gif_editor_choose)
            setOnClickListener { pick.launch(arrayOf("image/gif")) }
        })
        preview = ImageView(this).apply {
            adjustViewBounds = true
            minimumHeight = 130
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 200
        ))
        root.addView(title(R.string.gif_editor_start))
        start = SeekBar(this).apply { max = 99; progress = 0 }
        root.addView(start)
        root.addView(title(R.string.gif_editor_end))
        end = SeekBar(this).apply { max = 99; progress = 99 }
        root.addView(end)
        root.addView(title(R.string.gif_editor_speed))
        speed = SeekBar(this).apply { max = 150; progress = 50 }
        root.addView(speed)
        crop = CheckBox(this).apply { setText(R.string.gif_editor_crop) }
        root.addView(crop)
        caption = EditText(this).apply {
            hint = getString(R.string.gif_editor_overlay)
            maxLines = 2
        }
        root.addView(caption)
        status = title(R.string.gif_editor_tip)
        root.addView(status)
        save = Button(this).apply {
            setText(R.string.gif_editor_save)
            isEnabled = false
            setOnClickListener { export() }
        }
        root.addView(save)
        setContentView(ScrollView(this).apply { addView(root) })

        source = savedInstanceState?.getString("gif_uri")?.let(Uri::parse)
        if (source == null) {
            source = intent.getStringExtra("media_id")?.let {
                try { MediaShareProvider.uri(it) } catch (_: IllegalArgumentException) { null }
            }
        }
        if (source != null) showSelected(requireNotNull(source))
        else pick.launch(arrayOf("image/gif"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("gif_uri", source?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun export() {
        val uri = source ?: return
        val settings = AnimatedGifEditor.Options(
            startPercent = start.progress,
            endPercent = end.progress + 1,
            speedPercent = speed.progress + 50,
            centerCrop = crop.isChecked,
            overlay = caption.text.toString().take(80)
        )
        save.isEnabled = false
        status.setText(R.string.gif_editor_exporting)
        lifecycleScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val result = AnimatedGifEditor.render(
                        this@GifEditorActivity, uri, settings
                    )
                    MediaLibrary.importStream(
                        this@GifEditorActivity,
                        ByteArrayInputStream(result),
                        "gif-edit-" + System.currentTimeMillis() + ".gif"
                    )
                }
                Toast.makeText(this@GifEditorActivity,
                    getString(R.string.gif_editor_saved, file.displayName),
                    Toast.LENGTH_LONG).show()
                finish()
            } catch (error: Exception) {
                status.text = error.message ?: getString(R.string.gif_editor_invalid)
                save.isEnabled = true
            }
        }
    }
}
