package org.futo.inputmethod.latin.uix.actions.emoji

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.futo.inputmethod.latin.R

/**
 * Minimal offline installer for the same verified Fluent full-v2 collection as the lite APK.
 * The pack is stored in noBackupFilesDir, never shipped as part of the application.
 */
class FluentHqPackActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var importButton: Button
    private lateinit var removeButton: Button
    private var busy = false

    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            refreshState()
            status.setText(R.string.fluent_pack_importing)
            lifecycleScope.launch {
                try {
                    FluentHqPackStore.install(this@FluentHqPackActivity, uri)
                    status.setText(R.string.fluent_pack_installed)
                } catch (error: Exception) {
                    status.text = getString(
                        R.string.fluent_pack_import_failed,
                        error.localizedMessage ?: error.javaClass.simpleName
                    )
                } finally {
                    busy = false
                    refreshButtons()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val margin = (24f * density).toInt()
        val spacing = (12f * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, margin, margin, margin)
        }
        val heading = TextView(this).apply {
            setText(R.string.fluent_pack_title)
            textSize = 22f
        }
        val description = TextView(this).apply {
            setText(R.string.fluent_pack_description)
            textSize = 14f
        }
        status = TextView(this).apply { textSize = 16f }
        importButton = Button(this).apply {
            setText(R.string.fluent_pack_import)
            setOnClickListener {
                if (!busy) openDocument.launch(
                    arrayOf("application/zip", "application/octet-stream")
                )
            }
        }
        removeButton = Button(this).apply {
            setText(R.string.fluent_pack_remove)
            setOnClickListener {
                if (!busy) AlertDialog.Builder(this@FluentHqPackActivity)
                    .setMessage(R.string.fluent_pack_remove_confirm)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        busy = true
                        refreshButtons()
                        lifecycleScope.launch {
                            try {
                                FluentHqPackStore.uninstall(this@FluentHqPackActivity)
                                status.setText(R.string.fluent_pack_not_installed)
                            } catch (error: Exception) {
                                status.text = getString(
                                    R.string.fluent_pack_import_failed,
                                    error.localizedMessage ?: error.javaClass.simpleName
                                )
                            } finally {
                                busy = false
                                refreshState()
                            }
                        }
                    }.show()
            }
        }

        root.addView(heading)
        root.addView(description)
        for (control in listOf(status, importButton, removeButton)) {
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = spacing }
            root.addView(control, params)
        }
        setContentView(root)
        refreshState()
    }

    private fun refreshButtons() {
        importButton.isEnabled = !busy
        removeButton.isEnabled = !busy && FluentHqPackStore.isInstalled(this)
    }

    private fun refreshState() {
        if (!busy) {
            status.setText(
                if (FluentHqPackStore.isInstalled(this)) R.string.fluent_pack_installed
                else R.string.fluent_pack_not_installed
            )
        }
        refreshButtons()
    }
}
