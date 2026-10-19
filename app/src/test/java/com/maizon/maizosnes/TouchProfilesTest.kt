package com.maizon.maizosnes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchProfilesTest {
    @Test fun psxLegendsStillMapToSnesBits() {
        assertEquals("□", TouchPreset.PLAYSTATION.faceLegend(1))
        assertEquals("△", TouchPreset.PLAYSTATION.faceLegend(9))
        assertEquals("✕", TouchPreset.PLAYSTATION.faceLegend(0))
        assertEquals("○", TouchPreset.PLAYSTATION.faceLegend(8))
    }

    @Test fun xboxColorsHaveCorrectFaceOrdering() {
        assertEquals("X", TouchPreset.XBOX.faceLegend(1))
        assertEquals("Y", TouchPreset.XBOX.faceLegend(9))
        assertEquals("A", TouchPreset.XBOX.faceLegend(0))
        assertEquals("B", TouchPreset.XBOX.faceLegend(8))
        assertEquals("LB", TouchPreset.XBOX.shoulderLegend(10))
        assertEquals("RB", TouchPreset.XBOX.shoulderLegend(11))
    }

    @Test fun modernProfilesAreDifferentAndEditable() {
        assertTrue(TouchPreset.VIRTUAL_GAMEPAD.diamond)
        assertTrue(TouchPreset.PLAYSTATION.diamond)
        assertTrue(TouchPreset.XBOX.diamond)
        assertTrue(TouchPreset.SWITCH.diamond)
        assertTrue(TouchPreset.ARCADE.diamond)
        assertTrue(TouchPreset.STEAM.diamond)
        assertFalse(TouchPreset.MAIZO.diamond)
        assertEquals(10, TouchPreset.values().size)
        TouchPreset.values().forEach { preset ->
            val defaults = preset.defaultLayout()
            assertEquals(preset, defaults.preset)
            assertTrue(defaults.scale in 0.7f..1.45f)
            assertTrue(defaults.opacity in 0.2f..0.9f)
        }
    }
}
