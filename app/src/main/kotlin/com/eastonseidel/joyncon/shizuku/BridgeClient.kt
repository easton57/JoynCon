package com.eastonseidel.joyncon.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

data class DeviceStatus(
    val connected: Boolean = false,
    val grabbed: Boolean = false,
    val name: String = "",
    val rawCodesJson: String = "[]",
)

data class BridgeStatus(
    val bound: Boolean = false,
    val uinputActive: Boolean = false,
    val left: DeviceStatus = DeviceStatus(),
    val right: DeviceStatus = DeviceStatus(),
    val lastError: String? = null,
)

/**
 * App-process singleton that owns the Shizuku user-service connection and republishes its
 * (shell-UID) status as a StateFlow the UI can collect.
 */
object BridgeClient {

    private val _status = MutableStateFlow(BridgeStatus())
    val status: StateFlow<BridgeStatus> = _status

    private var bridge: IUInputBridge? = null
    private var bindRequested = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.eastonseidel.joyncon", UInputBridgeService::class.java.name)
    )
        .daemon(false)
        .processNameSuffix("bridge")
        .debuggable(false)
        .version(1)

    private val callback = object : IBridgeCallback.Stub() {
        override fun onDeviceGrabbed(role: String, deviceName: String, rawCodesJson: String) {
            updateDevice(role) { it.copy(connected = true, grabbed = true, name = deviceName, rawCodesJson = rawCodesJson) }
        }

        override fun onDeviceReleased(role: String) {
            updateDevice(role) { it.copy(grabbed = false) }
        }

        override fun onMergeStateChanged(uinputActive: Boolean, detail: String?) {
            _status.value = _status.value.copy(uinputActive = uinputActive)
        }

        override fun onError(message: String?) {
            _status.value = _status.value.copy(lastError = message)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val b = IUInputBridge.Stub.asInterface(service)
            bridge = b
            runCatching { b.setCallback(callback) }
            _status.value = _status.value.copy(bound = true, lastError = null)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            bridge = null
            bindRequested = false
            _status.value = _status.value.copy(bound = false).released()
        }
    }

    /**
     * Every call used to unconditionally re-invoke Shizuku.bindUserService with the same
     * (never-unbound) [connection], and on this Shizuku build that spawns a brand new bridge
     * process each time rather than reusing the existing one — repeated calls (each app restart's
     * ShizukuState collector, every "Reconnect bridge" tap) orphaned a fresh process per call
     * instead of connecting to what was already there. Guard so a bind is only ever in flight once.
     */
    fun bind(@Suppress("UNUSED_PARAMETER") context: Context) {
        if (ShizukuHelper.state.value != ShizukuState.READY) return
        if (bindRequested || _status.value.bound) return
        bindRequested = true
        Shizuku.bindUserService(userServiceArgs, connection)
    }

    fun unbind() {
        runCatching { bridge?.destroy() }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
        bridge = null
        bindRequested = false
        _status.value = _status.value.copy(bound = false).released()
    }

    fun scanRawDevices(): String = runCatching { bridge?.scanRawDevices() }.getOrNull() ?: "[]"

    fun startMerge(mappingJson: String): Boolean =
        runCatching { bridge?.startMerge(mappingJson) }.getOrNull() ?: false

    fun updateMapping(mappingJson: String) {
        runCatching { bridge?.updateMapping(mappingJson) }
    }

    fun stopMerge() {
        runCatching { bridge?.stopMerge() }
        // stopMerge() always releases everything; don't wait on (or depend on) the bridge's callback.
        _status.value = _status.value.released()
    }

    private fun BridgeStatus.released() = copy(
        uinputActive = false,
        left = left.copy(grabbed = false),
        right = right.copy(grabbed = false),
    )

    fun capabilities(): Pair<Boolean, Boolean> {
        val b = bridge ?: return false to false
        val uinput = runCatching { b.isUinputWritable() }.getOrDefault(false)
        val evdev = runCatching { b.isEvdevReadable() }.getOrDefault(false)
        return uinput to evdev
    }

    private inline fun updateDevice(role: String, transform: (DeviceStatus) -> DeviceStatus) {
        val current = _status.value
        _status.value = if (role == "left") {
            current.copy(left = transform(current.left))
        } else {
            current.copy(right = transform(current.right))
        }
    }
}
