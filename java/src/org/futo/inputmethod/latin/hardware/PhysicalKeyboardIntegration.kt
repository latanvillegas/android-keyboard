package org.futo.inputmethod.latin.hardware

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import org.futo.inputmethod.latin.R
import org.futo.inputmethod.latin.uix.settings.ScreenTitle

enum class VirtualKeyboardMode {
    SYSTEM, ALWAYS_SHOW, HIDE_WHEN_CONNECTED, MANUAL;
    companion object {
        fun parse(value: String?): VirtualKeyboardMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

internal fun resolveKeyboardVisibility(
    mode: VirtualKeyboardMode, connected: Boolean,
    manualShow: Boolean, nativeDecision: Boolean
): Boolean = when {
    mode == VirtualKeyboardMode.ALWAYS_SHOW -> true
    !connected || mode == VirtualKeyboardMode.SYSTEM -> nativeDecision
    mode == VirtualKeyboardMode.HIDE_WHEN_CONNECTED -> false
    else -> manualShow
}

internal object PhysicalKeyboardPrefs {
    const val FILE = "physical_keyboard_compat_v1"
    const val MODE = "virtual_keyboard_mode"
    const val MANUAL_SHOW = "manual_keyboard_visible"
}

/**
 * Real alphabetic USB/Bluetooth keyboards plus Lenovo integrated keyboard docks.
 * Do not require isExternal: OEM docks may appear as internal devices.
 * Do not consume Fn, Meta, AltGr, Ctrl, F-keys, numpad or multimedia events;
 * these belong to Android/Lenovo, the physical keyboard firmware and the editor.
 */
internal object PhysicalKeyboardDetector {
    fun names(context: Context): List<String> {
        val manager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
        val devices = manager.inputDeviceIds.mapNotNull { manager.getInputDevice(it) }
            .filter {
                !it.isVirtual &&
                    it.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
                    (it.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD
            }
            .map { it.name.ifBlank { "Physical keyboard" } }
            .distinct()
            .sorted()
        if (devices.isNotEmpty()) return devices

        val config = context.resources.configuration
        val hasDockLayout = config.keyboard == Configuration.KEYBOARD_QWERTY ||
            config.keyboard == Configuration.KEYBOARD_12KEY
        val usable = config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        return if (hasDockLayout && usable) listOf("Integrated keyboard / dock") else emptyList()
    }
}

/** Lifecycle-scoped IME integration: no global service and no extra permissions. */
internal class PhysicalKeyboardController(
    private val ime: InputMethodService
) : InputManager.InputDeviceListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val manager = ime.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val prefs = ime.getSharedPreferences(PhysicalKeyboardPrefs.FILE, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var attached = false
    private var deviceNames = PhysicalKeyboardDetector.names(ime)
    private var mode = VirtualKeyboardMode.parse(prefs.getString(PhysicalKeyboardPrefs.MODE, null))
    private var manualShow = prefs.getBoolean(PhysicalKeyboardPrefs.MANUAL_SHOW, true)

    fun attach() {
        if (attached) return
        attached = true
        manager.registerInputDeviceListener(this, handler)
        prefs.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    fun detach() {
        if (!attached) return
        attached = false
        manager.unregisterInputDeviceListener(this)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    fun shouldShow(nativeDecision: Boolean): Boolean =
        resolveKeyboardVisibility(mode, deviceNames.isNotEmpty(), manualShow, nativeDecision)

    fun refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { refresh() }
            return
        }
        val nextDevices = PhysicalKeyboardDetector.names(ime)
        val nextMode = VirtualKeyboardMode.parse(prefs.getString(PhysicalKeyboardPrefs.MODE, null))
        val nextManualShow = prefs.getBoolean(PhysicalKeyboardPrefs.MANUAL_SHOW, true)
        if (deviceNames == nextDevices && mode == nextMode && manualShow == nextManualShow) return
        deviceNames = nextDevices
        mode = nextMode
        manualShow = nextManualShow
        if (attached) ime.updateInputViewShown()
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) = refresh()
    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    override fun onSharedPreferenceChanged(preferences: SharedPreferences?, key: String?) {
        if (key == PhysicalKeyboardPrefs.MODE || key == PhysicalKeyboardPrefs.MANUAL_SHOW)
            refresh()
    }
}

/** This remains accessible from Settings even when the soft keyboard is hidden. */
@Composable
fun PhysicalKeyboardSettingsScreen(navController: NavHostController) {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
    }
    val prefs = remember(context) {
        context.getSharedPreferences(PhysicalKeyboardPrefs.FILE, Context.MODE_PRIVATE)
    }
    fun currentMode(): VirtualKeyboardMode =
        VirtualKeyboardMode.parse(prefs.getString(PhysicalKeyboardPrefs.MODE, null))
    var mode by remember(prefs) { mutableStateOf(currentMode()) }
    var manuallyVisible by remember(prefs) {
        mutableStateOf(prefs.getBoolean(PhysicalKeyboardPrefs.MANUAL_SHOW, true))
    }
    var connected by remember(context) { mutableStateOf(PhysicalKeyboardDetector.names(context)) }

    DisposableEffect(context, prefs, manager) {
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                connected = PhysicalKeyboardDetector.names(context)
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                connected = PhysicalKeyboardDetector.names(context)
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                connected = PhysicalKeyboardDetector.names(context)
            }
        }
        val preferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PhysicalKeyboardPrefs.MODE -> mode = currentMode()
                PhysicalKeyboardPrefs.MANUAL_SHOW ->
                    manuallyVisible = prefs.getBoolean(PhysicalKeyboardPrefs.MANUAL_SHOW, true)
            }
        }
        manager.registerInputDeviceListener(listener, Handler(Looper.getMainLooper()))
        prefs.registerOnSharedPreferenceChangeListener(preferencesListener)
        connected = PhysicalKeyboardDetector.names(context)
        onDispose {
            manager.unregisterInputDeviceListener(listener)
            prefs.unregisterOnSharedPreferenceChangeListener(preferencesListener)
        }
    }

    val options = listOf(
        VirtualKeyboardMode.SYSTEM to R.string.hardware_keyboard_mode_system,
        VirtualKeyboardMode.ALWAYS_SHOW to R.string.hardware_keyboard_mode_always,
        VirtualKeyboardMode.HIDE_WHEN_CONNECTED to R.string.hardware_keyboard_mode_hide,
        VirtualKeyboardMode.MANUAL to R.string.hardware_keyboard_mode_manual
    )
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitle(stringResource(R.string.hardware_keyboard_settings_title), true, navController)
        Text(
            if (connected.isEmpty()) stringResource(R.string.hardware_keyboard_disconnected)
            else stringResource(R.string.hardware_keyboard_connected) + ": " +
                connected.joinToString(", "),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            stringResource(R.string.hardware_keyboard_visibility_title),
            style = MaterialTheme.typography.titleMedium
        )
        options.forEach { (candidate, title) ->
            val select = {
                prefs.edit().putString(PhysicalKeyboardPrefs.MODE, candidate.name).apply()
                mode = candidate
            }
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = select)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = mode == candidate, onClick = select)
                Text(stringResource(title), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (mode == VirtualKeyboardMode.MANUAL) {
            Text(
                if (manuallyVisible) stringResource(R.string.hardware_keyboard_manual_on)
                else stringResource(R.string.hardware_keyboard_manual_off)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    prefs.edit().putBoolean(PhysicalKeyboardPrefs.MANUAL_SHOW, true).apply()
                    manuallyVisible = true
                }) { Text(stringResource(R.string.hardware_keyboard_show)) }
                OutlinedButton(onClick = {
                    prefs.edit().putBoolean(PhysicalKeyboardPrefs.MANUAL_SHOW, false).apply()
                    manuallyVisible = false
                }) { Text(stringResource(R.string.hardware_keyboard_hide)) }
            }
        }
        Text(
            stringResource(R.string.hardware_keyboard_mapping_notice),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
