package com.maizon.maizosnes

import android.view.KeyEvent

/** Only controller Guide/Mode is reserved for the emulator menu. */
internal object GamepadKeyRouting {
    const val OPTIONAL_GUIDE_BUTTON = 188
    fun isMenuButton(code: Int) =
        code == KeyEvent.KEYCODE_BUTTON_MODE || code == OPTIONAL_GUIDE_BUTTON
    fun isNativeStart(code: Int) = code == KeyEvent.KEYCODE_BUTTON_START
    fun isUiConfirm(code: Int) =
        code == KeyEvent.KEYCODE_BUTTON_A || code == KeyEvent.KEYCODE_DPAD_CENTER ||
            code == KeyEvent.KEYCODE_ENTER
    fun isUiBack(code: Int) =
        code == KeyEvent.KEYCODE_BUTTON_B || code == KeyEvent.KEYCODE_BACK
}
