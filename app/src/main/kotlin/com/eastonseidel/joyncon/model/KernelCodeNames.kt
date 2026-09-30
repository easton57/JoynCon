package com.eastonseidel.joyncon.model

/** Mirrors the small lookup table in joyncon_native.cpp, purely for display in the Remap UI. */
object KernelCodeNames {
    private val keyNames = mapOf(
        0x130 to "BTN_SOUTH", 0x131 to "BTN_EAST", 0x132 to "BTN_C", 0x133 to "BTN_NORTH",
        0x134 to "BTN_WEST", 0x135 to "BTN_Z", 0x136 to "BTN_TL", 0x137 to "BTN_TR",
        0x138 to "BTN_TL2", 0x139 to "BTN_TR2", 0x13a to "BTN_SELECT", 0x13b to "BTN_START",
        0x13c to "BTN_MODE", 0x13d to "BTN_THUMBL", 0x13e to "BTN_THUMBR",
        0x220 to "BTN_DPAD_UP", 0x221 to "BTN_DPAD_DOWN", 0x222 to "BTN_DPAD_LEFT", 0x223 to "BTN_DPAD_RIGHT",
        0x2c0 to "BTN_TRIGGER_HAPPY1", 0x2c1 to "BTN_TRIGGER_HAPPY2",
        0x2c2 to "BTN_TRIGGER_HAPPY3", 0x2c3 to "BTN_TRIGGER_HAPPY4",
    )

    private val absNames = mapOf(
        0x00 to "ABS_X", 0x01 to "ABS_Y", 0x02 to "ABS_Z", 0x03 to "ABS_RX", 0x04 to "ABS_RY",
        0x05 to "ABS_RZ", 0x10 to "ABS_HAT0X", 0x11 to "ABS_HAT0Y",
    )

    fun keyName(code: Int) = keyNames[code] ?: "KEY_$code"
    fun absName(code: Int) = absNames[code] ?: "ABS_$code"

    fun nameFor(type: RawCodeType, code: Int) = if (type == RawCodeType.KEY) keyName(code) else absName(code)
}
