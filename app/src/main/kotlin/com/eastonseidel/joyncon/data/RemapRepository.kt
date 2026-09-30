package com.eastonseidel.joyncon.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eastonseidel.joyncon.joycon.DefaultMapping
import com.eastonseidel.joyncon.model.RemapConfig
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "joyncon_prefs")
private val KEY_REMAP_JSON = stringPreferencesKey("remap_config_json")

private val json = Json { ignoreUnknownKeys = true }

class RemapRepository(private val context: Context) {

    val configFlow = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_REMAP_JSON]
        if (raw == null) {
            RemapConfig(entries = DefaultMapping.entries)
        } else {
            runCatching { json.decodeFromString(RemapConfig.serializer(), raw) }
                .getOrDefault(RemapConfig(entries = DefaultMapping.entries))
        }
    }

    suspend fun save(config: RemapConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_REMAP_JSON] = json.encodeToString(RemapConfig.serializer(), config)
        }
    }

    suspend fun resetToDefault() {
        save(RemapConfig(entries = DefaultMapping.entries))
    }
}
