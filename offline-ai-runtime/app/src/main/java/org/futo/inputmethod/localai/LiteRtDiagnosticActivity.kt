package org.futo.inputmethod.localai

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Diagnostic that exercises the exact signature-only Messenger IPC used by
 * FUTO's OfflineAiAction. It never sends text to the Internet, does not inspect
 * any editor and does not touch/delete the imported model.
 */
class LiteRtDiagnosticActivity : Activity() {
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var statusScroll: ScrollView
    private lateinit var testButton: Button
    private var boundConnection: ServiceConnection? = null
    private var bound = false
    private var timeout: Runnable? = null
    private var pendingToken = 0L
    private val model get() = File(File(filesDir, "llm"), "model.litertlm")

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private fun showStatus(message: String) {
        status.text = message
        statusScroll.post { statusScroll.scrollTo(0, 0) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.title = "Diagnóstico Qwen - FUTO"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(16))
            setBackgroundColor(Color.rgb(248, 249, 252))
            isFocusableInTouchMode = true
        }

        root.addView(TextView(this).apply {
            text = "Diagnóstico de IA Qwen"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(29, 36, 49))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        root.addView(TextView(this).apply {
            text = "Prueba local sin red. El resultado aparecerá completo aquí."
            textSize = 14f
            setTextColor(Color.rgb(75, 82, 93))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(18) })

        root.addView(TextView(this).apply {
            text = "RESULTADO DEL MOTOR"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(35, 62, 123))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        status = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.rgb(29, 36, 49))
            text = modelStatus()
            setPadding(dp(16), dp(18), dp(16), dp(18))
            setTextIsSelectable(true)
        }

        statusScroll = ScrollView(this).apply {
            isFillViewport = true
            isFocusable = false
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.rgb(215, 223, 234))
            }
            addView(status)
        }
        // Keep results near the heading; a weighted panel previously filled
        // almost the entire screen with whitespace.
        root.addView(statusScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(220)
        ).apply { bottomMargin = dp(16) })

        testButton = Button(this).apply {
            text = "PROBAR MOTOR QWEN AHORA"
            isAllCaps = false
            textSize = 16f
            setOnClickListener { testInference() }
        }
        root.addView(testButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(54)
        ).apply { bottomMargin = dp(8) })

        root.addView(Button(this).apply {
            text = "Copiar diagnóstico"
            isAllCaps = false
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Diagnóstico Qwen FUTO", status.text.toString())
                )
                android.widget.Toast.makeText(this@LiteRtDiagnosticActivity,
                    "Diagnóstico copiado", android.widget.Toast.LENGTH_SHORT).show()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { bottomMargin = dp(12) })

        root.addView(TextView(this).apply {
            text = "No descarga ni borra el modelo de 1,6 GB. " +
                "La primera generación puede tardar debido a su carga en memoria."
            textSize = 13f
            setTextColor(Color.rgb(75, 82, 93))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(root)
        root.requestFocus()
    }

    private fun modelStatus(): String {
        return if (model.isFile && model.length() > 1048576L) {
            "Modelo importado: " + (model.length() / 1048576) +
                " MiB\nArchivo: model.litertlm\n" +
                "Estado de inferencia: todavía sin probar"
        } else {
            "No hay un model.litertlm importado en el almacenamiento privado " +
                "del complemento. El archivo de Descargas no basta."
        }
    }

    private fun testInference() {
        if (!testButton.isEnabled) return
        if (!model.isFile || model.length() < 1048576L) {
            status.text = modelStatus()
            return
        }
        testButton.isEnabled = false
        showStatus("Modelo encontrado (" + (model.length() / 1048576) +
            " MiB). Inicializando LiteRT-LM y generando respuesta local...")
        val token = System.nanoTime()
        pendingToken = token
        val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
            if (message.what == LocalAiService.MSG_RESPONSE &&
                message.data.getLong("token", -1L) == pendingToken &&
                pendingToken != 0L) {
                val problem = message.data.getString("error")
                val response = message.data.getString("text").orEmpty()
                val result = when {
                    problem != null -> "ERROR DEL MOTOR:\n" + problem
                    response.isNotBlank() -> "IA FUNCIONANDO:\n" + response.take(1200)
                    else -> "ERROR: el servicio devolvió una respuesta vacía"
                }
                finishTest(result)
            }
            true
        })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                if (pendingToken != token) return
                try {
                    Messenger(binder).send(Message.obtain(null,
                        LocalAiService.MSG_GENERATE).apply {
                        replyTo = reply
                        data = Bundle().apply {
                            putLong("token", token)
                            putString("prompt",
                                "Responde en español únicamente con la palabra LISTO.")
                        }
                    })
                } catch (error: Exception) {
                    finishTest("ERROR IPC:\n" +
                        (error.localizedMessage ?: error.javaClass.simpleName))
                }
            }

            override fun onServiceDisconnected(name: ComponentName) {
                if (pendingToken == token) {
                    finishTest("ERROR: el proceso del motor IA se desconectó. " +
                        "Puede deberse a falta de memoria o fallo nativo.")
                }
            }

            override fun onNullBinding(name: ComponentName) {
                if (pendingToken == token) {
                    finishTest("ERROR: el servicio de IA rechazó la conexión.")
                }
            }
        }
        boundConnection = connection
        try {
            bound = bindService(
                Intent(this, LocalAiService::class.java),
                connection,
                Context.BIND_AUTO_CREATE
            )
            if (!bound) {
                finishTest("ERROR: Android no permitió iniciar el servicio de IA.")
                return
            }
            timeout = Runnable {
                if (pendingToken == token) {
                    finishTest("SIN RESPUESTA: el motor tardó más de 180 segundos. " +
                        "Comprueba RAM disponible y los registros de Android.")
                }
            }
            ui.postDelayed(checkNotNull(timeout), 180000L)
        } catch (error: Exception) {
            finishTest("ERROR AL CONECTAR:\n" +
                (error.localizedMessage ?: error.javaClass.simpleName))
        }
    }

    private fun finishTest(result: String) {
        if (pendingToken == 0L) return
        pendingToken = 0L
        timeout?.let { ui.removeCallbacks(it) }
        timeout = null
        if (bound) {
            val connection = boundConnection
            if (connection != null) {
                try { unbindService(connection) } catch (_: Exception) { }
            }
        }
        bound = false
        boundConnection = null
        if (!isFinishing && !isDestroyed) {
            showStatus(result)
            testButton.isEnabled = true
        }
    }

    override fun onDestroy() {
        pendingToken = 0L
        timeout?.let { ui.removeCallbacks(it) }
        timeout = null
        if (bound) {
            boundConnection?.let {
                try { unbindService(it) } catch (_: Exception) { }
            }
        }
        bound = false
        boundConnection = null
        super.onDestroy()
    }
}
