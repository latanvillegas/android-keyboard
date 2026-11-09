package org.futo.inputmethod.localai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Model import remains outside the keyboard, via SAF. Import .task bundles
 * compatible with MediaPipe 0.10.27; generic GGUF is not a supported format.
 */
class ModelSetupActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var choose: Button
    private val modelDirectory get() = File(filesDir, "llm")
    private val modelFile get() = File(modelDirectory, "model.task")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
        }
        layout.addView(TextView(this).apply {
            text = "Local AI — offline model configuration"
            textSize = 22f
        })
        status = TextView(this).apply {
            text = if (modelFile.isFile) {
                "Local model available: ${modelFile.length() / 1048576} MiB."
            } else {
                "Import a compatible MediaPipe LLM .task bundle. " +
                    "The model is optional; no model or user text is uploaded."
            }
            textSize = 17f
        }
        layout.addView(status)
        choose = Button(this).apply {
            text = "Import a model (.task)"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/octet-stream"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, 10)
            }
        }
        layout.addView(choose)
        layout.addView(Button(this).apply {
            text = "Remove model"
            setOnClickListener {
                status.text = if (modelFile.delete()) "Model removed"
                    else "No model installed, or removal failed"
            }
        })
        setContentView(layout)
    }

    @Deprecated("Activity result bridge without AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 10 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        choose.isEnabled = false
        status.text = "Copying model into private storage..."
        executor.execute {
            val result = try {
                importModel(uri)
            } catch (error: Exception) {
                "Import failed: " + (error.message ?: "incompatible file")
            }
            runOnUiThread {
                status.text = result
                choose.isEnabled = true
            }
        }
    }

    private fun importModel(uri: Uri): String {
        if (uri.scheme != "content") throw IllegalArgumentException("SAF files only")
        if (!modelDirectory.isDirectory && !modelDirectory.mkdirs())
            throw IllegalStateException("Cannot create private model directory")
        val staged = File.createTempFile("incoming-", ".task.tmp", modelDirectory)
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var length = 0L
            contentResolver.openInputStream(uri)?.use { input ->
                staged.outputStream().buffered().use { out ->
                    val chunk = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        length += count
                        if (length > 3L * 1024 * 1024 * 1024)
                            throw IllegalArgumentException("3 GiB model limit exceeded")
                        out.write(chunk, 0, count)
                        hash.update(chunk, 0, count)
                    }
                }
            } ?: throw IllegalStateException("Cannot open selected file")
            if (length < 1024 * 1024)
                throw IllegalArgumentException("Model file is unexpectedly small")
            val backup = File(modelDirectory, "model-previous.task")
            if (backup.exists() && !backup.delete())
                throw IllegalStateException("Cannot clear previous model backup")
            if (modelFile.exists() && !modelFile.renameTo(backup))
                throw IllegalStateException("Cannot back up existing model")
            if (!staged.renameTo(modelFile)) {
                if (backup.isFile) backup.renameTo(modelFile)
                throw IllegalStateException("Cannot activate new model")
            }
            backup.delete()
            return "Imported $length bytes (SHA-256: " +
                hash.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) } +
                "). Runtime will validate compatibility on first inference."
        } finally {
            staged.delete()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
