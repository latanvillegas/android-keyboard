package org.futo.inputmethod.latin.uix.actions.media

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.latin.R

/** Opens SAF from an Activity, not directly from the IME service. */
class MediaImportActivity : ComponentActivity() {
    private val pick = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { files ->
        if (files.isEmpty()) {
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                var imported = 0
                val errors = mutableListOf<String>()
                for (uri in files.take(100)) {
                    try {
                        imported += if (MediaPackImporter.hasZipSignature(this@MediaImportActivity, uri)) {
                            MediaPackImporter.importPack(this@MediaImportActivity, uri)
                        } else {
                            MediaLibrary.importUri(this@MediaImportActivity, uri)
                            1
                        }
                    } catch (error: Exception) {
                        errors.add(error.message ?: "Invalid media file")
                    }
                }
                imported to errors
            }
            val message = when {
                result.first > 0 && result.second.isEmpty() ->
                    getString(R.string.media_import_success, result.first)
                result.first > 0 ->
                    getString(R.string.media_import_partial, result.first, result.second.size)
                else -> result.second.firstOrNull() ?: getString(R.string.media_import_empty)
            }
            Toast.makeText(this@MediaImportActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.media_import_in_progress)
            textSize = 18f
            setPadding(28, 36, 28, 36)
        })
        if (savedInstanceState == null)
            pick.launch(arrayOf("image/*", "application/zip", "application/octet-stream"))
    }
}
