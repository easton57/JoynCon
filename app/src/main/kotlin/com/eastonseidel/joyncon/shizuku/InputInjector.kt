package com.eastonseidel.joyncon.shizuku

import android.os.IBinder
import android.view.InputEvent
import java.lang.reflect.Method
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Injects a synthetic [InputEvent] system-wide via the hidden IInputManager AIDL interface,
 * reached through a Shizuku-wrapped binder to android's "input" service. Shell (and therefore
 * Shizuku) holds android.permission.INJECT_EVENTS, the same permission `adb shell input` relies
 * on, so this works even though INJECT_EVENTS is a signature permission this app can't hold
 * directly. Used only by the Accessibility-fallback button path — the primary uinput path
 * (see UInputBridgeService) doesn't need this because it presents a real InputDevice instead.
 */
object InputInjector {
    private const val INJECT_INPUT_EVENT_MODE_ASYNC = 0

    private var iInputManager: Any? = null
    private var injectMethod: Method? = null
    private var initFailed = false

    private fun ensureReady(): Boolean {
        if (injectMethod != null) return true
        if (initFailed) return false
        val ready = runCatching {
            val binder: IBinder = SystemServiceHelper.getSystemService("input")
            val wrapped = ShizukuBinderWrapper(binder)
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            iInputManager = asInterface.invoke(null, wrapped)
            val ifaceClass = Class.forName("android.hardware.input.IInputManager")
            injectMethod = ifaceClass.methods.firstOrNull {
                it.name == "injectInputEvent" && it.parameterTypes.size == 2
            }
            injectMethod != null
        }.getOrDefault(false)
        if (!ready) initFailed = true
        return ready
    }

    fun inject(event: InputEvent): Boolean {
        if (!ensureReady()) return false
        return runCatching {
            injectMethod!!.invoke(iInputManager, event, INJECT_INPUT_EVENT_MODE_ASYNC) as? Boolean ?: false
        }.getOrDefault(false)
    }
}
