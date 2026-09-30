package com.eastonseidel.joyncon.service

import android.content.Context
import android.hardware.input.InputManager
import com.eastonseidel.joyncon.joycon.JoyConIds
import com.eastonseidel.joyncon.model.JoyConRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class NativeConnectionState(val leftSeen: Boolean = false, val rightSeen: Boolean = false)

/**
 * Zero-permission, InputManager-only connection signal. Watches Android's InputDeviceListener for
 * the whole process lifetime (from [init]), so the UI reflects Joy-Cons connecting/disconnecting
 * even before [MergeForegroundService] is started; the service reacts to [state] rather than
 * listening itself.
 */
object ConnectionState {
    private val _state = MutableStateFlow(NativeConnectionState())
    val state: StateFlow<NativeConnectionState> = _state

    private lateinit var inputManager: InputManager

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = rescan()
        override fun onInputDeviceRemoved(deviceId: Int) = rescan()
        override fun onInputDeviceChanged(deviceId: Int) = rescan()
    }

    fun init(context: Context) {
        if (::inputManager.isInitialized) return
        inputManager = context.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(deviceListener, null)
        rescan()
    }

    fun rescan() {
        if (!::inputManager.isInitialized) return
        var left = false
        var right = false
        for (id in inputManager.inputDeviceIds) {
            val device = inputManager.getInputDevice(id) ?: continue
            if (device.vendorId != JoyConIds.VENDOR_NINTENDO) continue
            when (JoyConIds.roleFor(device.productId)) {
                JoyConRole.LEFT -> left = true
                JoyConRole.RIGHT -> right = true
                null -> {}
            }
        }
        _state.value = NativeConnectionState(left, right)
    }
}
