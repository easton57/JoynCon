package com.eastonseidel.joyncon.model

import android.view.KeyEvent

/**
 * Maps merged output buttons to standard Android gamepad KeyEvent codes, for the Accessibility
 * fallback path only (it injects real Android KeyEvents rather than uinput reports, since it has
 * no way to create a persistent virtual InputDevice — see README "Two capture paths").
 * Capture/SL/SR have no standard gamepad keycode; the generic BUTTON_9..13 codes are used so they
 * still reach games that let you bind "any" button, even though most games won't recognize them.
 */
object LogicalOutputKeyMap {
    private val map = mapOf(
        LogicalOutput.BUTTON_A to KeyEvent.KEYCODE_BUTTON_A,
        LogicalOutput.BUTTON_B to KeyEvent.KEYCODE_BUTTON_B,
        LogicalOutput.BUTTON_X to KeyEvent.KEYCODE_BUTTON_X,
        LogicalOutput.BUTTON_Y to KeyEvent.KEYCODE_BUTTON_Y,
        LogicalOutput.BUMPER_L to KeyEvent.KEYCODE_BUTTON_L1,
        LogicalOutput.BUMPER_R to KeyEvent.KEYCODE_BUTTON_R1,
        LogicalOutput.TRIGGER_ZL to KeyEvent.KEYCODE_BUTTON_L2,
        LogicalOutput.TRIGGER_ZR to KeyEvent.KEYCODE_BUTTON_R2,
        LogicalOutput.BUTTON_MINUS to KeyEvent.KEYCODE_BUTTON_SELECT,
        LogicalOutput.BUTTON_PLUS to KeyEvent.KEYCODE_BUTTON_START,
        LogicalOutput.BUTTON_HOME to KeyEvent.KEYCODE_BUTTON_MODE,
        LogicalOutput.STICK_L_CLICK to KeyEvent.KEYCODE_BUTTON_THUMBL,
        LogicalOutput.STICK_R_CLICK to KeyEvent.KEYCODE_BUTTON_THUMBR,
        LogicalOutput.DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,
        LogicalOutput.DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        LogicalOutput.DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        LogicalOutput.DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
        LogicalOutput.BUTTON_CAPTURE to KeyEvent.KEYCODE_BUTTON_13,
        LogicalOutput.RAIL_SL_LEFT to KeyEvent.KEYCODE_BUTTON_9,
        LogicalOutput.RAIL_SR_LEFT to KeyEvent.KEYCODE_BUTTON_10,
        LogicalOutput.RAIL_SL_RIGHT to KeyEvent.KEYCODE_BUTTON_11,
        LogicalOutput.RAIL_SR_RIGHT to KeyEvent.KEYCODE_BUTTON_12,
    )

    fun keyCodeFor(output: LogicalOutput): Int? = map[output]
}
