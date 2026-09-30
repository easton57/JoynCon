package com.eastonseidel.joyncon.shizuku

import android.os.Process
import android.util.Log
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/**
 * Runs inside the Shizuku user-service process, i.e. as UID 2000 (shell), not as this app's own
 * UID. That is what gives [nativeStartMerge] permission to open /dev/input/eventX and /dev/uinput
 * on devices whose SELinux policy grants "shell" access to the input group — see the README's
 * "How this actually works" section for exactly what this can and cannot do without root.
 *
 * Instantiated by Shizuku via reflection, so it must keep this public no-arg constructor.
 */
class UInputBridgeService : IUInputBridge.Stub() {

    private var callback: IBridgeCallback? = null
    private var poller: ScheduledExecutorService? = null
    private var lastStatusJson: String = ""

    init {
        killStaleBridgeSiblings()
        System.loadLibrary("joyncon_native")
    }

    override fun setCallback(cb: IBridgeCallback?) {
        callback = cb
    }

    override fun scanRawDevices(): String = runCatching { nativeScanRawDevices() }.getOrElse {
        Log.e(TAG, "scanRawDevices failed", it)
        "[]"
    }

    override fun startMerge(mappingJson: String): Boolean {
        val killed = killStaleBridgeSiblings()
        if (killed > 0) {
            callback?.onError("Cleaned up $killed ghost bridge process(es) left over from a previous run")
        }
        val ok = runCatching { nativeStartMerge(mappingJson) }.getOrElse {
            Log.e(TAG, "startMerge failed", it)
            callback?.onError("startMerge threw: ${it.message}")
            false
        }
        startPolling()
        return ok
    }

    override fun updateMapping(mappingJson: String) {
        runCatching { nativeUpdateMapping(mappingJson) }.onFailure {
            callback?.onError("updateMapping threw: ${it.message}")
        }
    }

    override fun stopMerge() {
        runCatching { nativeStopMerge() }.onFailure {
            callback?.onError("stopMerge threw: ${it.message}")
        }
        stopPolling()
        // The poller is gone, so push the post-release (ungrabbed) status once ourselves — otherwise
        // the app keeps showing the last "merged" snapshot forever.
        lastStatusJson = ""
        pollOnce()
    }

    override fun isUinputWritable(): Boolean = runCatching { nativeIsUinputWritable() }.getOrDefault(false)

    override fun isEvdevReadable(): Boolean = runCatching { nativeIsEvdevReadable() }.getOrDefault(false)

    override fun destroy() {
        stopMerge()
        poller?.shutdownNow()
    }

    private fun startPolling() {
        if (poller != null) return
        poller = Executors.newSingleThreadScheduledExecutor().also { exec ->
            exec.scheduleWithFixedDelay({ pollOnce() }, 0, 500, TimeUnit.MILLISECONDS)
        }
    }

    private fun stopPolling() {
        poller?.shutdownNow()
        poller = null
    }

    private fun pollOnce() {
        val statusJson = runCatching { nativeGetStatusJson() }.getOrElse { return }
        if (statusJson == lastStatusJson) return
        lastStatusJson = statusJson
        val cb = callback ?: return
        runCatching {
            val root = JSONObject(statusJson)
            for (role in listOf("left", "right")) {
                val obj = root.optJSONObject(role) ?: continue
                val grabbed = obj.optBoolean("grabbed", false)
                val name = obj.optString("name", "")
                if (grabbed) {
                    cb.onDeviceGrabbed(role, name, obj.optString("rawCodes", "[]"))
                } else {
                    cb.onDeviceReleased(role)
                }
            }
            cb.onMergeStateChanged(root.optBoolean("uinputActive", false), root.optString("detail", ""))
        }
    }

    /**
     * Shizuku's non-daemon UserService is supposed to die with the client that bound it, but that
     * teardown hasn't proven reliable here — a killed/force-stopped app process (or the OS
     * reclaiming memory) can leave a previous bridge process running forever, still holding
     * EVIOCGRAB on the physical Joy-Cons and its own /dev/uinput virtual gamepad, silently
     * blocking every future merge attempt. This instance and any stale sibling both run as the
     * same (shell) UID, so a same-UID kill() is always permitted regardless of Shizuku's own
     * bookkeeping — clean up before touching any device ourselves.
     */
    private fun killStaleBridgeSiblings(): Int {
        val myPid = Process.myPid()
        val pidDirs = File("/proc").listFiles { f -> f.isDirectory && f.name.toIntOrNull() != null } ?: return 0
        var killed = 0
        for (dir in pidDirs) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid == myPid) continue
            val cmdline = runCatching {
                File(dir, "cmdline").readBytes().toString(Charsets.UTF_8).trim('\u0000')
            }.getOrNull() ?: continue
            if (cmdline == "com.eastonseidel.joyncon:bridge") {
                Log.w(TAG, "Killing stale bridge sibling process $pid")
                if (runCatching { Process.killProcess(pid) }.isSuccess) killed++
            }
        }
        return killed
    }

    private external fun nativeScanRawDevices(): String
    private external fun nativeStartMerge(mappingJson: String): Boolean
    private external fun nativeUpdateMapping(mappingJson: String)
    private external fun nativeStopMerge()
    private external fun nativeIsUinputWritable(): Boolean
    private external fun nativeIsEvdevReadable(): Boolean
    private external fun nativeGetStatusJson(): String

    companion object {
        private const val TAG = "UInputBridgeService"
    }
}
