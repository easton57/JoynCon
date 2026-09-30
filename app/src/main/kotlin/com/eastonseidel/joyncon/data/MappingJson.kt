package com.eastonseidel.joyncon.data

import com.eastonseidel.joyncon.model.RemapConfig
import com.eastonseidel.joyncon.model.StickCalibration
import org.json.JSONArray
import org.json.JSONObject

/** Wire format shared with the native merge loop (see app/src/main/cpp/joyncon_native.cpp). */
object MappingJson {

    fun encode(config: RemapConfig): String {
        val entries = JSONArray()
        for (entry in config.entries) {
            entries.put(
                JSONObject().apply {
                    put("role", entry.role.name)
                    put("rawType", entry.rawType.name)
                    put("rawCode", entry.rawCode)
                    put("output", entry.output.name)
                }
            )
        }
        return JSONObject().apply {
            put("entries", entries)
            put("leftCal", calJson(config.leftStickCalibration))
            put("rightCal", calJson(config.rightStickCalibration))
        }.toString()
    }

    private fun calJson(cal: StickCalibration) = JSONObject().apply {
        put("deadzone", cal.deadzonePercent)
        put("invertX", cal.invertX)
        put("invertY", cal.invertY)
    }
}
