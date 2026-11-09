package org.futo.inputmethod.latin.uix.actions.media

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R

/** SAF export without broad filesystem permissions or network access. */
class MediaExportActivity : ComponentActivity() {
    private val picker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) {
            finish()
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            try {
                val count = MediaPackExporter.export(this@MediaExportActivity, uri)
                Toast.makeText(this@MediaExportActivity,
                    getString(R.string.media_export_success, count),
                    Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(this@MediaExportActivity,
                    error.message ?: getString(R.string.media_export_failure),
                    Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = getString(R.string.media_export_in_progress)
            textSize = 18f
            setPadding(28, 36, 28, 36)
        })
        if (savedInstanceState == null) picker.launch("futo-media-pack.zip")
    }
}
