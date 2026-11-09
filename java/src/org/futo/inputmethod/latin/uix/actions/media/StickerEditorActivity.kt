package org.futo.inputmethod.latin.uix.actions.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
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
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Offline static-sticker creator. Original source remains untouched.
 * Animated GIF input deliberately edits its first frame as a static image.
 */
class StickerEditorActivity : ComponentActivity() {
    private var source: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var caption: EditText
    private lateinit var saveButton: Button

    private val pick = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    val bitmap = withContext(Dispatchers.IO) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, bounds)
                        }
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
                            bounds.outWidth > 4096 || bounds.outHeight > 4096 ||
                            bounds.outWidth.toLong() * bounds.outHeight > 16_777_216L) {
                            throw IOException("Unsupported source dimensions")
                        }
                        var sample = 1
                        while (bounds.outWidth / sample > 1024 ||
                            bounds.outHeight / sample > 1024) sample *= 2
                        val options = BitmapFactory.Options().apply {
                            inSampleSize = sample
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it, null, options)
                        } ?: throw IOException("Image decoder failed")
                    }
                    source = bitmap
                    preview.setImageBitmap(bitmap)
                    saveButton.isEnabled = true
                } catch (error: Exception) {
                    Toast.makeText(this@StickerEditorActivity,
                        error.message ?: getString(R.string.media_import_empty),
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        layout.addView(TextView(this).apply {
            text = getString(R.string.media_editor_title)
            textSize = 22f
        })
        layout.addView(Button(this).apply {
            text = getString(R.string.media_choose_photo)
            setOnClickListener { pick.launch("image/*") }
        })
        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        layout.addView(preview, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        caption = EditText(this).apply {
            hint = getString(R.string.media_editor_caption)
            maxLines = 2
        }
        layout.addView(caption)
        saveButton = Button(this).apply {
            text = getString(R.string.media_save_sticker)
            isEnabled = false
            setOnClickListener { saveSticker() }
        }
        layout.addView(saveButton)
        setContentView(layout)
        if (savedInstanceState == null) pick.launch("image/*")
    }

    private fun saveSticker() {
        val original = source ?: return
        val label = caption.text.toString().trim().take(80)
        saveButton.isEnabled = false
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val size = 512
                    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(output)
                    val scale = minOf(
                        size.toFloat() / original.width,
                        size.toFloat() / original.height
                    )
                    val width = original.width * scale
                    val height = original.height * scale
                    val rectangle = RectF(
                        (size - width) / 2, (size - height) / 2,
                        (size + width) / 2, (size + height) / 2
                    )
                    canvas.drawBitmap(original, null, rectangle, Paint(Paint.FILTER_BITMAP_FLAG))
                    if (label.isNotEmpty()) {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            textAlign = Paint.Align.CENTER
                            textSize = 44f
                        }
                        while (paint.measureText(label) > 460f && paint.textSize > 18f) {
                            paint.textSize -= 2f
                        }
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = 7f
                        paint.color = Color.BLACK
                        canvas.drawText(label, 256f, 474f, paint)
                        paint.style = Paint.Style.FILL
                        paint.color = Color.WHITE
                        canvas.drawText(label, 256f, 474f, paint)
                    }
                    val bytes = ByteArrayOutputStream()
                    if (!output.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                        throw IOException("PNG encoder failed")
                    output.recycle()
                    MediaLibrary.importStream(
                        this@StickerEditorActivity,
                        ByteArrayInputStream(bytes.toByteArray()),
                        "sticker-" + System.currentTimeMillis() + ".png"
                    )
                }
                Toast.makeText(this@StickerEditorActivity,
                    R.string.media_sticker_saved, Toast.LENGTH_LONG).show()
                finish()
            } catch (error: Exception) {
                Toast.makeText(this@StickerEditorActivity,
                    error.message ?: getString(R.string.media_import_empty),
                    Toast.LENGTH_LONG).show()
                saveButton.isEnabled = true
            }
        }
    }
}
