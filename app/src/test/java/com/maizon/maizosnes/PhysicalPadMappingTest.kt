package com.maizon.maizosnes

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class PhysicalPadMappingTest {
    @Test fun defaultStartAndTwoProfilesIndependent() {
        val initial = PhysicalPadMapping.defaults
        assertEquals(3, PhysicalPadMapping.bitForKey(initial, KeyEvent.KEYCODE_BUTTON_START))
        val p2 = PhysicalPadMapping.rebind(initial, 8, KeyEvent.KEYCODE_BUTTON_Y)
        assertEquals(8, PhysicalPadMapping.bitForKey(p2, KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(1, PhysicalPadMapping.bitForKey(p2, KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(1, PhysicalPadMapping.bitForKey(initial, KeyEvent.KEYCODE_BUTTON_Y))
    }

    @Test fun guideAndBackCannotBeHijacked() {
        assertFalse(PhysicalPadMapping.mayBind(KeyEvent.KEYCODE_BUTTON_MODE))
        assertFalse(PhysicalPadMapping.mayBind(KeyEvent.KEYCODE_BACK))
        assertEquals(PhysicalPadMapping.defaults,
            PhysicalPadMapping.rebind(PhysicalPadMapping.defaults, 3,
                KeyEvent.KEYCODE_BUTTON_MODE))
    }

    @Test fun survivesPersistenceAndRejectsMalformedData() {
        val map = PhysicalPadMapping.rebind(PhysicalPadMapping.defaults, 2,
            KeyEvent.KEYCODE_BUTTON_L1)
        assertEquals(map, PhysicalPadMapping.decode(PhysicalPadMapping.encode(map)))
        assertEquals(PhysicalPadMapping.defaults, PhysicalPadMapping.decode("bad"))
        assertEquals(PhysicalPadMapping.defaults, PhysicalPadMapping.decode("3:188"))
    }
}
