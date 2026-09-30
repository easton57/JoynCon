package com.eastonseidel.joyncon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eastonseidel.joyncon.service.ConnectionState
import com.eastonseidel.joyncon.shizuku.BridgeClient
import com.eastonseidel.joyncon.shizuku.ShizukuHelper
import com.eastonseidel.joyncon.shizuku.ShizukuState

@Composable
fun StatusScreen(onStartService: () -> Unit, onStopService: () -> Unit) {
    val context = LocalContext.current
    val shizukuState by ShizukuHelper.state.collectAsState()
    val bridgeStatus by BridgeClient.status.collectAsState()
    val connection by ConnectionState.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("JoynCon", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Shizuku", style = MaterialTheme.typography.titleMedium)
                val running = shizukuState != ShizukuState.NOT_INSTALLED && shizukuState != ShizukuState.NOT_RUNNING
                StatusRow(
                    color = if (running) COLOR_OK else COLOR_OFF,
                    text = "Service: " + if (running) "running" else "not running",
                )
                StatusRow(
                    color = when {
                        bridgeStatus.bound -> COLOR_OK
                        shizukuState == ShizukuState.READY -> COLOR_PENDING
                        else -> COLOR_OFF
                    },
                    text = "Bridge: " + when {
                        bridgeStatus.bound -> "connected"
                        shizukuState == ShizukuState.READY -> "not connected (starts with the merge service)"
                        else -> "not connected"
                    },
                )
                Text(shizukuStateLabel(shizukuState))
                if (shizukuState == ShizukuState.NOT_INSTALLED) {
                    Button(onClick = { ShizukuHelper.openPlayStore(context) }) {
                        Text("Get Shizuku on Google Play")
                    }
                }
                if (shizukuState == ShizukuState.NOT_RUNNING) {
                    Button(onClick = { ShizukuHelper.openShizukuApp(context) }) {
                        Text("Open Shizuku")
                    }
                }
                if (shizukuState == ShizukuState.RUNNING_NO_PERMISSION || shizukuState == ShizukuState.RUNNING_DENIED) {
                    Button(onClick = { ShizukuHelper.requestPermission() }) {
                        Text("Grant Shizuku permission")
                    }
                }
                if (shizukuState == ShizukuState.READY) {
                    Button(onClick = { BridgeClient.bind(context) }) {
                        Text("Reconnect bridge")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Joy-Cons", style = MaterialTheme.typography.titleMedium)
                DeviceRow("Left", connection.leftSeen, bridgeStatus.left.grabbed)
                DeviceRow("Right", connection.rightSeen, bridgeStatus.right.grabbed)
                Text(modeLabel(connection.leftSeen, connection.rightSeen, bridgeStatus.uinputActive))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onStartService) { Text("Start merge service") }
            Button(onClick = onStopService) { Text("Stop") }
        }

        bridgeStatus.lastError?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("Last error: $error", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private val COLOR_OK = Color(0xFF00E5A0)
private val COLOR_PENDING = Color(0xFFFFC107)
private val COLOR_OFF = Color(0xFF888888)

@Composable
private fun DeviceRow(label: String, seen: Boolean, bridgeGrabbed: Boolean) {
    // Android's input-device list is the authority on presence: a Joy-Con that just dropped off
    // can't be "merged", even if the bridge hasn't reported the release yet.
    val grabbed = seen && bridgeGrabbed
    StatusRow(
        color = when {
            grabbed -> COLOR_OK
            seen -> COLOR_PENDING
            else -> COLOR_OFF
        },
        text = "Joy-Con ($label): " + when {
            grabbed -> "merged"
            seen -> "connected, not yet grabbed"
            else -> "not connected"
        },
    )
}

@Composable
private fun StatusRow(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        ) {}
        Text(text)
    }
}

private fun shizukuStateLabel(state: ShizukuState) = when (state) {
    ShizukuState.NOT_INSTALLED -> "Not installed — JoynCon needs the free Shizuku app to merge controllers"
    ShizukuState.NOT_RUNNING -> "Not running — open the Shizuku app and start it (see Setup tab)"
    ShizukuState.RUNNING_NO_PERMISSION -> "Running, permission not yet requested"
    ShizukuState.RUNNING_DENIED -> "Running, permission denied — grant it in Shizuku"
    ShizukuState.READY -> "Ready"
}

private fun modeLabel(left: Boolean, right: Boolean, uinputActive: Boolean) = when {
    left && right && uinputActive -> "Mode: full merge (virtual gamepad active)"
    left && right -> "Mode: both connected, waiting on Shizuku bridge"
    left || right -> "Mode: passthrough (only one Joy-Con connected)"
    else -> "Mode: idle"
}
