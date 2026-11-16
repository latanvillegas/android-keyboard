package org.futo.inputmethod.localai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.StatFs
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * User-driven import of an external .litertlm model.
 * Downloads happen in the user's browser, never in the IME process.
 */
class LiteRtModelSetupActivity : Activity() {
    private companion object {
        const val PICK_REQUEST = 71
        const val MAX_BYTES = 3L * 1024 * 1024 * 1024
        const val MIN_BYTES = 1024L * 1024
        const val KNOWN_FILENAME =
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm"
        const val KNOWN_SHA =
            "faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9"
        const val DOWNLOAD_URL =
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/" +
            "19edb84c69a0212f29a6ef17ba0d6f278b6a1614/" + KNOWN_FILENAME
    }

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var pickButton: Button
    private val directory get() = File(filesDir, "llm")
    private val model get() = File(directory, "model.litertlm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(30, 35, 30, 35)
        }
        panel.addView(TextView(this).apply {
            text = "FUTO - Qwen offline (LiteRT-LM)"
            textSize = 22f
        })
        status = TextView(this).apply {
            text = if (model.isFile) {
                "LiteRT model installed: " + (model.length() / 1048576) + " MiB"
            } else {
                "No LiteRT model installed. Download a .litertlm file and import it."
            }
            textSize = 16f
        }
        panel.addView(status)
        panel.addView(Button(this).apply {
            text = "Download Qwen 1.5B (1.6 GB) in browser"
            setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)))
                } catch (_: Exception) {
                    status.text = "No browser is available"
                }
            }
        })
        pickButton = Button(this).apply {
            text = "Import .litertlm from file"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/octet-stream"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, PICK_REQUEST)
            }
        }
        panel.addView(pickButton)
        panel.addView(Button(this).apply {
            text = "Remove LiteRT model (preserve MediaPipe .task)"
            setOnClickListener {
                status.text = if (model.isFile && model.delete()) {
                    "LiteRT model removed"
                } else {
                    "No LiteRT model installed or removal failed"
                }
            }
        })
        setContentView(panel)
    }

    @Deprecated("Platform SAF callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_REQUEST || resultCode != RESULT_OK) return
        val selected = data?.data ?: return
        pickButton.isEnabled = false
        status.text = "Importing and hashing model..."
        executor.execute {
            val message = try {
                importModel(selected)
            } catch (error: Exception) {
                "Import failed: " + (error.localizedMessage ?: error.javaClass.simpleName)
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    status.text = message
                    pickButton.isEnabled = true
                }
            }
        }
    }

    private fun displayName(uri: Uri): String {
        return contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: ""
    }

    private fun importModel(uri: Uri): String {
        if (uri.scheme != "content") throw IOException("Select a SAF document")
        val name = displayName(uri)
        if (!name.endsWith(".litertlm", ignoreCase = true))
            throw IOException("Expected .litertlm, not .task or GGUF")
        if (!directory.isDirectory && !directory.mkdirs())
            throw IOException("Private model directory unavailable")
        if (StatFs(directory.absolutePath).availableBytes < 512L * 1024 * 1024)
            throw IOException("Insufficient free storage")
        val staged = File.createTempFile("incoming-", ".litertlm.tmp", directory)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().buffered().use { output ->
                    val bytes = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(bytes)
                        if (count == -1) break
                        if (count == 0) continue
                        copied += count
                        if (copied > MAX_BYTES) throw IOException("3 GiB limit exceeded")
                        output.write(bytes, 0, count)
                        digest.update(bytes, 0, count)
                    }
                }
            } ?: throw IOException("Unable to open model document")
            if (copied < MIN_BYTES) throw IOException("Model is too small")
            val sha = digest.digest().joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
            if (name == KNOWN_FILENAME && sha != KNOWN_SHA)
                throw IOException("Pinned Qwen SHA-256 mismatch")
            val backup = File(directory, "model-previous.litertlm")
            if (backup.exists() && !backup.delete())
                throw IOException("Cannot remove previous backup")
            if (model.exists() && !model.renameTo(backup))
                throw IOException("Cannot back up installed model")
            if (!staged.renameTo(model)) {
                if (backup.isFile) backup.renameTo(model)
                throw IOException("Cannot activate model")
            }
            backup.delete()
            return "Imported " + (copied / 1048576) +
                " MiB; SHA-256: " + sha + ". Ready for offline inference."
        } finally {
            staged.delete()
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
