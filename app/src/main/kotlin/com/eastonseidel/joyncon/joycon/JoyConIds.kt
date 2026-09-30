package com.eastonseidel.joyncon.joycon

import com.eastonseidel.joyncon.model.JoyConRole
import com.eastonseidel.joyncon.model.LogicalOutput
import com.eastonseidel.joyncon.model.MappingEntry
import com.eastonseidel.joyncon.model.RawCodeType

/** Nintendo's USB-IF vendor ID and the Joy-Con product IDs, as reported by struct input_id over EVIOCGID. */
object JoyConIds {
    const val VENDOR_NINTENDO = 0x057e
    const val PRODUCT_JOYCON_L = 0x2006
    const val PRODUCT_JOYCON_R = 0x2007
    const val PRODUCT_PRO_CONTROLLER = 0x2009

    fun roleFor(productId: Int): JoyConRole? = when (productId) {
        PRODUCT_JOYCON_L -> JoyConRole.LEFT
        PRODUCT_JOYCON_R -> JoyConRole.RIGHT
        else -> null
    }
}

/**
 * Best-effort default mapping for the mainline Linux `hid-nintendo` driver's evdev output.
 * Kernel keycode assignments for Joy-Cons are not officially stable across driver/Android versions,
 * so this is a starting point only — use the in-app "Detect raw codes" flow on the Remap screen to
 * confirm (or fix) these against the exact phone/driver combination before relying on them.
 */
object DefaultMapping {
    // linux/input-event-codes.h
    private const val BTN_SOUTH = 0x130
    private const val BTN_EAST = 0x131
    private const val BTN_NORTH = 0x133
    private const val BTN_WEST = 0x134
    private const val BTN_Z = 0x135
    private const val BTN_TL = 0x136
    private const val BTN_TR = 0x137
    private const val BTN_TL2 = 0x138
    private const val BTN_TR2 = 0x139
    private const val BTN_SELECT = 0x13a
    private const val BTN_START = 0x13b
    private const val BTN_MODE = 0x13c
    private const val BTN_THUMBL = 0x13d
    private const val BTN_THUMBR = 0x13e
    private const val BTN_DPAD_UP = 0x220
    private const val BTN_DPAD_DOWN = 0x221
    private const val BTN_DPAD_LEFT = 0x222
    private const val BTN_DPAD_RIGHT = 0x223
    private const val BTN_TRIGGER_HAPPY1 = 0x2c0
    private const val BTN_TRIGGER_HAPPY2 = 0x2c1
    private const val BTN_TRIGGER_HAPPY3 = 0x2c2
    private const val BTN_TRIGGER_HAPPY4 = 0x2c3

    private const val ABS_X = 0x00
    private const val ABS_Y = 0x01
    private const val ABS_RX = 0x03
    private const val ABS_RY = 0x04

    val entries: List<MappingEntry> = buildList {
        // Left Joy-Con: D-pad, L/ZL, -, stick click, SL/SR, left analog stick.
        add(key(JoyConRole.LEFT, BTN_DPAD_UP, LogicalOutput.DPAD_UP))
        add(key(JoyConRole.LEFT, BTN_DPAD_DOWN, LogicalOutput.DPAD_DOWN))
        add(key(JoyConRole.LEFT, BTN_DPAD_LEFT, LogicalOutput.DPAD_LEFT))
        add(key(JoyConRole.LEFT, BTN_DPAD_RIGHT, LogicalOutput.DPAD_RIGHT))
        add(key(JoyConRole.LEFT, BTN_TL, LogicalOutput.BUMPER_L))
        add(key(JoyConRole.LEFT, BTN_TL2, LogicalOutput.TRIGGER_ZL_AXIS))
        add(key(JoyConRole.LEFT, BTN_SELECT, LogicalOutput.BUTTON_MINUS))
        add(key(JoyConRole.LEFT, BTN_THUMBL, LogicalOutput.STICK_L_CLICK))
        add(key(JoyConRole.LEFT, BTN_Z, LogicalOutput.BUTTON_CAPTURE))
        add(key(JoyConRole.LEFT, BTN_TRIGGER_HAPPY1, LogicalOutput.RAIL_SL_LEFT))
        add(key(JoyConRole.LEFT, BTN_TRIGGER_HAPPY2, LogicalOutput.RAIL_SR_LEFT))
        add(abs(JoyConRole.LEFT, ABS_X, LogicalOutput.LEFT_STICK_X))
        add(abs(JoyConRole.LEFT, ABS_Y, LogicalOutput.LEFT_STICK_Y))

        // Right Joy-Con: face buttons, R/ZR, +, Home, stick click, SL/SR, right analog stick.
        add(key(JoyConRole.RIGHT, BTN_SOUTH, LogicalOutput.BUTTON_B))
        add(key(JoyConRole.RIGHT, BTN_EAST, LogicalOutput.BUTTON_A))
        add(key(JoyConRole.RIGHT, BTN_NORTH, LogicalOutput.BUTTON_X))
        add(key(JoyConRole.RIGHT, BTN_WEST, LogicalOutput.BUTTON_Y))
        add(key(JoyConRole.RIGHT, BTN_TR, LogicalOutput.BUMPER_R))
        add(key(JoyConRole.RIGHT, BTN_TR2, LogicalOutput.TRIGGER_ZR_AXIS))
        add(key(JoyConRole.RIGHT, BTN_START, LogicalOutput.BUTTON_PLUS))
        add(key(JoyConRole.RIGHT, BTN_MODE, LogicalOutput.BUTTON_HOME))
        add(key(JoyConRole.RIGHT, BTN_THUMBR, LogicalOutput.STICK_R_CLICK))
        add(key(JoyConRole.RIGHT, BTN_TRIGGER_HAPPY3, LogicalOutput.RAIL_SL_RIGHT))
        add(key(JoyConRole.RIGHT, BTN_TRIGGER_HAPPY4, LogicalOutput.RAIL_SR_RIGHT))
        add(abs(JoyConRole.RIGHT, ABS_RX, LogicalOutput.RIGHT_STICK_X))
        add(abs(JoyConRole.RIGHT, ABS_RY, LogicalOutput.RIGHT_STICK_Y))
    }

    private fun key(role: JoyConRole, code: Int, output: LogicalOutput) =
        MappingEntry(role, RawCodeType.KEY, code, output)

    private fun abs(role: JoyConRole, code: Int, output: LogicalOutput) =
        MappingEntry(role, RawCodeType.ABS, code, output)
}
