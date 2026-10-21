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
    @Test fun perButtonSizeAndOpacityPersistWithoutBreakingLegacyProfiles() {
        val preset = TouchPreset.VIRTUAL_GAMEPAD
        val edited = preset.defaultLayout().copy(
            buttonShifts = mapOf(8 to ButtonShift(.15f, -.07f)),
            buttonStyles = mapOf(
                4 to ButtonStyle(1.3f, .7f),
                8 to ButtonStyle(.75f, .55f),
                3 to ButtonStyle(1.2f, .9f))
        )
        val restored = TouchLayoutCodec.decode(TouchLayoutCodec.encode(edited), preset)!!
        assertEquals(1.3f, restored.buttonStyles[4]!!.scale, .001f)
        assertEquals(.55f, restored.buttonStyles[8]!!.opacity, .001f)
        assertEquals(.15f, restored.buttonShifts[8]!!.x, .001f)
        assertEquals(.07f, -restored.buttonShifts[8]!!.y, .001f)
        assertEquals(.60f, ButtonStyle(-100f, 2f).sanitized().scale, .001f)
        assertEquals(1f, ButtonStyle(-100f, 2f).sanitized().opacity, .001f)
        val legacy = TouchLayoutCodec.decode("""{"preset":"VIRTUAL_GAMEPAD"}""", preset)!!
        assertTrue(legacy.buttonStyles.isEmpty())
    }

}
