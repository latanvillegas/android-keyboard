package org.futo.inputmethod.latin.uix.actions.media

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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optional locally installed AI runtime, separate from the keyboard binary.
 * Same-signature IPC only; no Internet permission, server URL or telemetry.
 */
internal object LocalAiClient {
    private const val PACKAGE = "org.futo.inputmethod.localai"
    private const val SERVICE = "$PACKAGE.LocalAiService"
    private const val SETUP = "$PACKAGE.ModelSetupActivity"
    private const val MSG_GENERATE = 100
    private const val MSG_RESPONSE = 101

    fun openSetup(context: Context): Boolean = try {
        context.startActivity(Intent().setClassName(PACKAGE, SETUP)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: Exception) {
        false
    }

    suspend fun generate(context: Context, prompt: String): String {
        require(prompt.isNotBlank() && prompt.length <= 2500)
        return withTimeout(120_000) {
            suspendCancellableCoroutine { continuation ->
                val app = context.applicationContext
                val token = System.nanoTime()
                val released = AtomicBoolean(false)
                var bound = false
                lateinit var connection: ServiceConnection
                fun cleanup() {
                    if (released.compareAndSet(false, true) && bound) {
                        try { app.unbindService(connection) } catch (_: Exception) {}
                    }
                }
                val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
                    if (message.what == MSG_RESPONSE &&
                        message.data.getLong("token", -1) == token) {
                        if (continuation.isActive) {
                            val error = message.data.getString("error")
                            if (error != null)
                                continuation.resumeWithException(IllegalStateException(error))
                            else
                                continuation.resume(message.data.getString("text").orEmpty())
                        }
                        cleanup()
                    }
                    true
                })
                connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                        try {
                            val request = Message.obtain(null, MSG_GENERATE)
                            request.replyTo = reply
                            request.data = Bundle().apply {
                                putString("prompt", prompt)
                                putLong("token", token)
                            }
                            Messenger(binder).send(request)
                        } catch (error: Exception) {
                            if (continuation.isActive)
                                continuation.resumeWithException(error)
                            cleanup()
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Local AI runtime disconnected")
                            )
                        }
                        cleanup()
                    }
                }
                try {
                    bound = app.bindService(Intent().setClassName(PACKAGE, SERVICE),
                        connection, Context.BIND_AUTO_CREATE)
                    if (!bound) {
                        if (continuation.isActive)
                            continuation.resumeWithException(
                                IllegalStateException(
                                    "Install the separately signed Local AI runtime APK"
                                )
                            )
                        cleanup()
                    }
                } catch (error: Exception) {
                    if (continuation.isActive)
                        continuation.resumeWithException(error)
                    cleanup()
                }
                continuation.invokeOnCancellation { cleanup() }
            }
        }
    }
}
