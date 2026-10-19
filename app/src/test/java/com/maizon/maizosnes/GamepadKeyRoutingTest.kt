package com.maizon.maizosnes

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class GamepadKeyRoutingTest {
    @Test fun startIsNativeAndModeIsMenu() {
        assertTrue(GamepadKeyRouting.isNativeStart(KeyEvent.KEYCODE_BUTTON_START))
        assertFalse(GamepadKeyRouting.isMenuButton(KeyEvent.KEYCODE_BUTTON_START))
        assertTrue(GamepadKeyRouting.isMenuButton(KeyEvent.KEYCODE_BUTTON_MODE))
    }
    @Test fun selectionKeysDoNotMapToBack() {
        assertTrue(GamepadKeyRouting.isUiConfirm(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(GamepadKeyRouting.isUiConfirm(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(GamepadKeyRouting.isUiBack(KeyEvent.KEYCODE_BUTTON_B))
        assertFalse(GamepadKeyRouting.isUiBack(KeyEvent.KEYCODE_BUTTON_A))
    }
}
