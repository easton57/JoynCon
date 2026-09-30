package com.eastonseidel.joyncon.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.eastonseidel.joyncon.R
import com.eastonseidel.joyncon.shizuku.BridgeClient
import com.eastonseidel.joyncon.shizuku.ShizukuHelper
import com.eastonseidel.joyncon.shizuku.ShizukuState

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val shizukuState by ShizukuHelper.state.collectAsState()
    var capabilityResult by remember { mutableStateOf<String?>(null) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    if (showAccessibilityDisclosure) {
        // Google Play's Accessibility API policy requires this in-app disclosure and an affirmative
        // "accept" before sending the user to enable the service.
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text(stringResource(R.string.accessibility_disclosure_title)) },
            text = { Text(stringResource(R.string.accessibility_disclosure_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showAccessibilityDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) { Text("No thanks") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Setup", style = MaterialTheme.typography.headlineMedium)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("One-time Shizuku setup", style = MaterialTheme.typography.titleMedium)
                Text("1. Install Shizuku from Google Play.")
                if (shizukuState == ShizukuState.NOT_INSTALLED) {
                    Button(onClick = { ShizukuHelper.openPlayStore(context) }) { Text("Get Shizuku on Google Play") }
                }
                Text("2. In the Shizuku app, start it via \"Wireless debugging\" — pair your phone with itself over ADB from Android Settings → Developer options → Wireless debugging → Pair with pairing code, then paste the pairing info into Shizuku.")
                Text("3. Come back to JoynCon and tap \"Grant Shizuku permission\" on the Status tab.")
                Text("4. Shizuku has to be restarted after every phone reboot — that's a Shizuku limitation, not JoynCon's.")
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Accessibility fallback (buttons only)", style = MaterialTheme.typography.titleMedium)
                Text("Only needed if the capability check below says uinput/evdev access is blocked on this phone. It lets JoynCon remap buttons system-wide a second way, but it can never merge analog sticks — see the README for why.")
                Button(onClick = { showAccessibilityDisclosure = true }) { Text("Open Accessibility settings") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Capability self-check", style = MaterialTheme.typography.titleMedium)
                Text("Requires the Shizuku bridge to be connected first (Status tab).")
                Button(onClick = {
                    val (uinput, evdev) = BridgeClient.capabilities()
                    capabilityResult = "uinput writable: $uinput · evdev readable: $evdev"
                }) { Text("Run check") }
                capabilityResult?.let { Text(it) }
            }
        }
    }
}
