package org.futo.inputmethod.localai

import android.app.Activity
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
    private lateinit var testButton: Button
    private var boundConnection: ServiceConnection? = null
    private var bound = false
    private var timeout: Runnable? = null
    private var pendingToken = 0L
    private val model get() = File(File(filesDir, "llm"), "model.litertlm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 36, 32, 36)
        }
        panel.addView(TextView(this).apply {
            text = "Diagnóstico de IA Qwen sin conexión"
            textSize = 21f
        })
        status = TextView(this).apply {
            textSize = 16f
            text = modelStatus()
            setPadding(0, 16, 0, 22)
        }
        panel.addView(status)
        testButton = Button(this).apply {
            text = "PROBAR MOTOR QWEN AHORA"
            setOnClickListener { testInference() }
        }
        panel.addView(testButton)
        panel.addView(TextView(this).apply {
            text = "Esta prueba envía una petición fija al mismo servicio que usa FUTO. " +
                "No lee chats, contraseñas ni otro texto. " +
                "La primera generación puede tardar al cargar 1,6 GB de modelo."
            textSize = 14f
        })
        setContentView(ScrollView(this).apply { addView(panel) })
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
        status.text = "Modelo encontrado (" + (model.length() / 1048576) +
            " MiB). Inicializando LiteRT-LM y generando respuesta local..."
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
            status.text = result
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
