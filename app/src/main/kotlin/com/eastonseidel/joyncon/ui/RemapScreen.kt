package com.eastonseidel.joyncon.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eastonseidel.joyncon.data.MappingJson
import com.eastonseidel.joyncon.data.RemapRepository
import com.eastonseidel.joyncon.model.JoyConRole
import com.eastonseidel.joyncon.model.KernelCodeNames
import com.eastonseidel.joyncon.model.LogicalOutput
import com.eastonseidel.joyncon.model.MappingEntry
import com.eastonseidel.joyncon.model.RawCodeType
import com.eastonseidel.joyncon.model.RemapConfig
import com.eastonseidel.joyncon.shizuku.BridgeClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

private data class DetectedCode(val role: JoyConRole, val type: RawCodeType, val code: Int, val name: String)

@Composable
fun RemapScreen() {
    val context = LocalContext.current
    val repo = remember { RemapRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val config by repo.configFlow.collectAsState(initial = RemapConfig())

    var detected by remember { mutableStateOf<List<DetectedCode>>(emptyList()) }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Remap", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tap an entry to change what it does on the merged controller. \"Detect raw codes\" " +
                "reads what the connected Joy-Cons actually report on this phone — the default " +
                "mapping below is a best guess and may need correcting.",
            style = MaterialTheme.typography.bodySmall,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                val json = BridgeClient.scanRawDevices()
                detected = parseDetected(json)
            }) { Text("Detect raw codes") }

            OutlinedButton(onClick = {
                scope.launch {
                    repo.resetToDefault()
                    BridgeClient.updateMapping(MappingJson.encode(repo.configFlow.first()))
                }
            }) { Text("Reset to default") }
        }

        HorizontalDivider()

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(config.entries) { entry ->
                MappingRow(entry) { newOutput ->
                    val updated = config.with(entry.copy(output = newOutput))
                    scope.launch {
                        repo.save(updated)
                        BridgeClient.updateMapping(MappingJson.encode(updated))
                    }
                }
            }

            if (detected.isNotEmpty()) {
                item { HorizontalDivider() }
                item { Text("Detected but not yet mapped", style = MaterialTheme.typography.titleSmall) }
                items(detected.filter { d -> config.entries.none { it.role == d.role && it.rawType == d.type && it.rawCode == d.code } }) { d ->
                    DetectedRow(d) { output ->
                        val updated = config.with(MappingEntry(d.role, d.type, d.code, output))
                        scope.launch {
                            repo.save(updated)
                            BridgeClient.updateMapping(MappingJson.encode(updated))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MappingRow(entry: MappingEntry, onPick: (LogicalOutput) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${entry.role} · ${KernelCodeNames.nameFor(entry.rawType, entry.rawCode)}")
        }
        Column {
            Button(onClick = { expanded = true }) { Text(entry.output.label) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LogicalOutput.values().forEach { output ->
                    DropdownMenuItem(text = { Text(output.label) }, onClick = {
                        expanded = false
                        onPick(output)
                    })
                }
            }
        }
    }
}

@Composable
private fun DetectedRow(d: DetectedCode, onPick: (LogicalOutput) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text("${d.role} · ${d.name}")
        }
        Column {
            Button(onClick = { expanded = true }) { Text("Assign") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LogicalOutput.values().forEach { output ->
                    DropdownMenuItem(text = { Text(output.label) }, onClick = {
                        expanded = false
                        onPick(output)
                    })
                }
            }
        }
    }
}

private fun parseDetected(json: String): List<DetectedCode> {
    val result = mutableListOf<DetectedCode>()
    runCatching {
        val root = JSONObject(json)
        for ((key, role) in listOf("left" to JoyConRole.LEFT, "right" to JoyConRole.RIGHT)) {
            val obj = root.optJSONObject(key) ?: continue
            val codes = obj.optJSONArray("codes") ?: continue
            for (i in 0 until codes.length()) {
                val c = codes.getJSONObject(i)
                val type = if (c.getString("type") == "ABS") RawCodeType.ABS else RawCodeType.KEY
                result += DetectedCode(role, type, c.getInt("code"), c.getString("name"))
            }
        }
    }
    return result
}
