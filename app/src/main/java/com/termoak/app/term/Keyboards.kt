package com.termoak.app.term

import android.content.Context
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Whether a hardware keyboard is attached: Bluetooth or USB, a tablet's
 * cover, a Chromebook's or DeX's. Then the terminal doesn't open the
 * on-screen keyboard by itself and hides the key bar (unless the setting
 * keeps it).
 */
class HardwareKeyboard(private val context: Context) : InputManager.InputDeviceListener {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    init {
        context.getSystemService(InputManager::class.java)?.registerInputDeviceListener(this, Handler(Looper.getMainLooper()))
        refresh(context.resources.configuration)
    }

    /** On a configuration change (the activity's, which has the keyboard's state). */
    fun refresh(config: Configuration) {
        val fromConfig = config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        _connected.value = fromConfig || externalKeyboard()
    }

    /** A real (not virtual) keyboard with letters that was plugged or paired. */
    private fun externalKeyboard(): Boolean = InputDevice.getDeviceIds().any { id ->
        val d = InputDevice.getDevice(id) ?: return@any false
        !d.isVirtual && d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC &&
            d.supportsSource(InputDevice.SOURCE_KEYBOARD) && (Build.VERSION.SDK_INT < 29 || d.isExternal)
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh(context.resources.configuration)
    override fun onInputDeviceRemoved(deviceId: Int) = refresh(context.resources.configuration)
    override fun onInputDeviceChanged(deviceId: Int) = refresh(context.resources.configuration)
}

/** The layout of the keyboard a key came from (the one chosen for it in the system settings). */
class AndroidKeyLayout(private val map: KeyCharacterMap) : KeyLayout {
    override fun char(keyCode: Int, shift: Boolean, altGr: Boolean, capsLock: Boolean, numLock: Boolean): Int {
        var meta = 0
        if (shift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        if (altGr) meta = meta or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON
        if (capsLock) meta = meta or KeyEvent.META_CAPS_LOCK_ON
        if (numLock) meta = meta or KeyEvent.META_NUM_LOCK_ON
        return map.get(keyCode, meta)
    }
}

fun KeyEvent.layout(): KeyLayout = AndroidKeyLayout(keyCharacterMap)

fun KeyEvent.toKeyPress(): KeyPress {
    val right = metaState and KeyEvent.META_ALT_RIGHT_ON != 0
    val left = metaState and KeyEvent.META_ALT_LEFT_ON != 0
    return KeyPress(
        keyCode = keyCode,
        shift = isShiftPressed,
        ctrl = isCtrlPressed,
        // An Alt without side (synthesized events) is a left Alt.
        alt = left || (isAltPressed && !right),
        altGr = right,
        meta = isMetaPressed,
        capsLock = isCapsLockOn,
        numLock = isNumLockOn,
        repeat = repeatCount,
    )
}
