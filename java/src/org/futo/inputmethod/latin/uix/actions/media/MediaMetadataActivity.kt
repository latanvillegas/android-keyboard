package org.futo.inputmethod.latin.uix.actions.media

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R

/**
 * Tags, optional collections and explicit sticker-vs-photo classification.
 * Changing metadata does not recompress or overwrite original image bytes.
 */
class MediaMetadataActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ID = "media_id"
    }

    private lateinit var nameField: EditText
    private lateinit var tagsField: EditText
    private lateinit var collectionField: EditText
    private lateinit var typePicker: Spinner
    private lateinit var save: Button
    private lateinit var message: TextView
    private val typeOptions = listOf(MediaContentType.STICKER, MediaContentType.IMAGE)

    private fun dp(n: Int) = (n * resources.displayMetrics.density + .5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mediaId = intent.getStringExtra(EXTRA_ID).orEmpty()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }
        fun label(id: Int) {
            root.addView(TextView(this).apply {
                setText(id)
                textSize = 15f
                setPadding(0, dp(10), 0, dp(2))
            })
        }
        root.addView(TextView(this).apply {
            setText(R.string.media_metadata_title)
            textSize = 22f
        })
        label(R.string.media_metadata_name)
        nameField = EditText(this).apply {
            setSingleLine(true)
            filters = arrayOf(android.text.InputFilter.LengthFilter(100))
        }
        root.addView(nameField)
        label(R.string.media_metadata_tags)
        tagsField = EditText(this).apply {
            setSingleLine(false)
            maxLines = 3
            hint = getString(R.string.media_metadata_tags_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(350))
        }
        root.addView(tagsField)
        label(R.string.media_metadata_collection)
        collectionField = EditText(this).apply {
            setSingleLine(true)
            hint = getString(R.string.media_metadata_collection_hint)
            filters = arrayOf(android.text.InputFilter.LengthFilter(48))
        }
        root.addView(collectionField)
        label(R.string.media_metadata_type)
        typePicker = Spinner(this)
        typePicker.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf(
                getString(R.string.media_stickers),
                getString(R.string.media_images)
            )
        )
        root.addView(typePicker)
        message = TextView(this).apply {
            gravity = Gravity.START
            setPadding(0, dp(12), 0, dp(12))
            setText(R.string.media_metadata_unaltered)
        }
        root.addView(message)
        save = Button(this).apply {
            setText(R.string.ai_variant_save)
            isEnabled = false
        }
        root.addView(save, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
        ))
        setContentView(ScrollView(this).apply { addView(root) })
        lifecycleScope.launch {
            val item = withContext(Dispatchers.IO) { MediaLibrary.get(this@MediaMetadataActivity, mediaId) }
            if (item == null) {
                message.setText(R.string.media_missing)
                return@launch
            }
            nameField.setText(item.displayName)
            tagsField.setText(item.tags.joinToString(", "))
            collectionField.setText(item.collection)
            if (item.contentType == MediaContentType.IMAGE) typePicker.setSelection(1)
            if (item.kind == MediaKind.GIF) {
                typePicker.isEnabled = false
                message.setText(R.string.media_metadata_gif_type)
            }
            save.isEnabled = true
            save.setOnClickListener {
                val name = nameField.text.toString().trim()
                if (name.isBlank()) {
                    nameField.error = getString(R.string.media_metadata_required)
                    return@setOnClickListener
                }
                save.isEnabled = false
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            MediaLibrary.updateMetadata(
                                this@MediaMetadataActivity,
                                item.id,
                                name,
                                tagsField.text.toString(),
                                collectionField.text.toString(),
                                if (item.kind == MediaKind.GIF) MediaContentType.GIF
                                else typeOptions[typePicker.selectedItemPosition.coerceIn(0, 1)]
                            )
                        }
                        finish()
                    } catch (error: Exception) {
                        save.isEnabled = true
                        Toast.makeText(this@MediaMetadataActivity,
                            error.localizedMessage ?: getString(R.string.media_metadata_error),
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
