package com.eastonseidel.joyncon.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.eastonseidel.joyncon.data.RemapRepository
import com.eastonseidel.joyncon.joycon.JoyConIds
import com.eastonseidel.joyncon.model.JoyConRole
import com.eastonseidel.joyncon.model.LogicalOutput
import com.eastonseidel.joyncon.model.LogicalOutputKeyMap
import com.eastonseidel.joyncon.model.RawCodeType
import com.eastonseidel.joyncon.model.RemapConfig
import com.eastonseidel.joyncon.service.ConnectionState
import com.eastonseidel.joyncon.shizuku.InputInjector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fallback path used only when the Shizuku evdev/uinput path (UInputBridgeService) can't get raw
 * device access on this phone's SELinux policy. AccessibilityService key-event filtering is a
 * genuine exception to Android's normal focus-gated input dispatch — it can see and consume
 * KeyEvents system-wide regardless of which app is focused (this is also how no-root button
 * remappers like Button Mapper work) — but there is no equivalent API for joystick MotionEvents,
 * so this path only ever merges buttons. Analog sticks are not remapped here; see the README.
 */
class JoyConAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var config: RemapConfig = RemapConfig()

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch {
            RemapRepository(applicationContext).configFlow.collect { config = it }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Unused: this service exists only for its onKeyEvent filtering, not for a11y tree events.
    }

    override fun onInterrupt() {}

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val device = event.device ?: return false
        if (device.vendorId != JoyConIds.VENDOR_NINTENDO) return false
        val role = JoyConIds.roleFor(device.productId) ?: return false

        val connection = ConnectionState.state.value
        if (!(connection.leftSeen && connection.rightSeen)) return false // single Joy-Con: passthrough (req #6)

        val output = config.outputFor(role, RawCodeType.KEY, event.keyCode)
        if (output == LogicalOutput.UNMAPPED) return false

        val outCode = LogicalOutputKeyMap.keyCodeFor(output) ?: return false
        val synthetic = KeyEvent(
            event.downTime, event.eventTime, event.action, outCode, event.repeatCount,
            0, 0, event.scanCode, event.flags,
            android.view.InputDevice.SOURCE_GAMEPAD or android.view.InputDevice.SOURCE_JOYSTICK,
        )
        InputInjector.inject(synthetic)
        return true
    }
}
