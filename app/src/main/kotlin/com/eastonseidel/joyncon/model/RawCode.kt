package com.eastonseidel.joyncon.model

import kotlinx.serialization.Serializable

enum class RawCodeType { KEY, ABS }

@Serializable
data class RawCode(
    val type: RawCodeType,
    val code: Int,
    /** Linux kernel constant name, e.g. "BTN_SOUTH" or "ABS_RX", as reported by the native evdev scanner. */
    val kernelName: String,
)

enum class JoyConRole { LEFT, RIGHT }

/** The unified output layout JoynCon presents through the virtual /dev/uinput gamepad. */
enum class LogicalOutput(val label: String) {
    BUTTON_A("A"),
    BUTTON_B("B"),
    BUTTON_X("X"),
    BUTTON_Y("Y"),
    BUMPER_L("L"),
    BUMPER_R("R"),
    TRIGGER_ZL("ZL"),
    TRIGGER_ZR("ZR"),
    /**
     * Same physical ZL/ZR press, but written to the virtual gamepad as a full-swing analog trigger
     * axis (kernel ABS_Z/ABS_RZ) instead of a button (BTN_TL2/BTN_TR2). Some games — Unity's default
     * Android gamepad profile included — read shoulder triggers as AXIS_LTRIGGER/AXIS_RTRIGGER only
     * and never see a plain button press there, even though Joy-Cons don't have pressure-sensitive
     * triggers to begin with (it's just digital press -> 0 or max).
     */
    TRIGGER_ZL_AXIS("ZL (as analog trigger axis)"),
    TRIGGER_ZR_AXIS("ZR (as analog trigger axis)"),
    BUTTON_MINUS("-"),
    BUTTON_PLUS("+"),
    BUTTON_HOME("Home"),
    BUTTON_CAPTURE("Capture"),
    STICK_L_CLICK("L3"),
    STICK_R_CLICK("R3"),
    RAIL_SL_LEFT("SL (left rail)"),
    RAIL_SR_LEFT("SR (left rail)"),
    RAIL_SL_RIGHT("SL (right rail)"),
    RAIL_SR_RIGHT("SR (right rail)"),
    DPAD_UP("D-Pad Up"),
    DPAD_DOWN("D-Pad Down"),
    DPAD_LEFT("D-Pad Left"),
    DPAD_RIGHT("D-Pad Right"),
    LEFT_STICK_X("Left stick X"),
    LEFT_STICK_Y("Left stick Y"),
    RIGHT_STICK_X("Right stick X"),
    RIGHT_STICK_Y("Right stick Y"),
    UNMAPPED("Unmapped");

    val isAxis: Boolean
        get() = this == LEFT_STICK_X || this == LEFT_STICK_Y || this == RIGHT_STICK_X || this == RIGHT_STICK_Y ||
            this == TRIGGER_ZL_AXIS || this == TRIGGER_ZR_AXIS
}
