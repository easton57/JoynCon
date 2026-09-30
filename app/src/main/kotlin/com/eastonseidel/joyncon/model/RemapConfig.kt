package com.eastonseidel.joyncon.model

import kotlinx.serialization.Serializable

@Serializable
data class MappingEntry(
    val role: JoyConRole,
    val rawType: RawCodeType,
    val rawCode: Int,
    val output: LogicalOutput,
)

@Serializable
data class StickCalibration(
    val deadzonePercent: Int = 12,
    val invertX: Boolean = false,
    val invertY: Boolean = false,
)

@Serializable
data class RemapConfig(
    val entries: List<MappingEntry> = emptyList(),
    val leftStickCalibration: StickCalibration = StickCalibration(),
    val rightStickCalibration: StickCalibration = StickCalibration(),
) {
    fun outputFor(role: JoyConRole, type: RawCodeType, code: Int): LogicalOutput =
        entries.firstOrNull { it.role == role && it.rawType == type && it.rawCode == code }?.output
            ?: LogicalOutput.UNMAPPED

    fun with(entry: MappingEntry): RemapConfig {
        val filtered = entries.filterNot { it.role == entry.role && it.rawType == entry.rawType && it.rawCode == entry.rawCode }
        return copy(entries = filtered + entry)
    }
}
