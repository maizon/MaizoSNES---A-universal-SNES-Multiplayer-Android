package com.maizon.maizosnes

import android.view.KeyEvent

/**
 * Independent P1/P2 physical-button profiles. The emulation interface always
 * receives the same 0..15 Libretro bits; no protocol/core/input ABI changes.
 */
internal object PhysicalPadMapping {
    val buttons: List<Pair<Int, String>> = listOf(
        4 to "↑", 5 to "↓", 6 to "←", 7 to "→",
        0 to "B", 1 to "Y", 8 to "A", 9 to "X",
        2 to "SELECT", 3 to "START", 10 to "L", 11 to "R",
        12 to "L2", 13 to "R2", 14 to "L3", 15 to "R3"
    )

    val defaults: Map<Int, Int> = linkedMapOf(
        4 to KeyEvent.KEYCODE_DPAD_UP,
        5 to KeyEvent.KEYCODE_DPAD_DOWN,
        6 to KeyEvent.KEYCODE_DPAD_LEFT,
        7 to KeyEvent.KEYCODE_DPAD_RIGHT,
        0 to KeyEvent.KEYCODE_BUTTON_B,
        1 to KeyEvent.KEYCODE_BUTTON_Y,
        8 to KeyEvent.KEYCODE_BUTTON_A,
        9 to KeyEvent.KEYCODE_BUTTON_X,
        2 to KeyEvent.KEYCODE_BUTTON_SELECT,
        3 to KeyEvent.KEYCODE_BUTTON_START,
        10 to KeyEvent.KEYCODE_BUTTON_L1,
        11 to KeyEvent.KEYCODE_BUTTON_R1,
        12 to KeyEvent.KEYCODE_BUTTON_L2,
        13 to KeyEvent.KEYCODE_BUTTON_R2,
        14 to KeyEvent.KEYCODE_BUTTON_THUMBL,
        15 to KeyEvent.KEYCODE_BUTTON_THUMBR
    )

    /** Guide/Home and Android back stay reserved for the OS/overlay. */
    fun mayBind(keyCode: Int): Boolean =
        keyCode in 7..300 &&
            keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KeyEvent.KEYCODE_HOME &&
            !GamepadKeyRouting.isMenuButton(keyCode) &&
            keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
            keyCode != KeyEvent.KEYCODE_VOLUME_DOWN

    fun bitForKey(bindings: Map<Int, Int>, keyCode: Int): Int? =
        bindings.entries.firstOrNull { it.value == keyCode }?.key

    /** Swap conflicting assignments; never emit two bits for one physical key. */
    fun rebind(current: Map<Int, Int>, bit: Int, keyCode: Int): Map<Int, Int> {
        if (bit !in 0..15 || !mayBind(keyCode)) return current
        val old = current[bit] ?: return current
        if (old == keyCode) return current
        val changed = current.toMutableMap()
        val other = changed.entries.firstOrNull { it.key != bit && it.value == keyCode }?.key
        changed[bit] = keyCode
        if (other != null) changed[other] = old
        return changed
    }

    /** ASCII persistence requires no Android JSON services on unit-test JVM. */
    fun encode(bindings: Map<Int, Int>): String =
        buttons.joinToString(";") { (bit, _) -> "$bit:${bindings[bit] ?: defaults[bit]}" }

    fun decode(saved: String?): Map<Int, Int> {
        if (saved.isNullOrBlank()) return defaults
        val pairs = saved.split(';')
        if (pairs.size != defaults.size) return defaults
        val parsed = linkedMapOf<Int, Int>()
        for (pair in pairs) {
            val (bitText, codeText) = pair.split(':').takeIf { it.size == 2 }
                ?: return defaults
            val bit = bitText.toIntOrNull() ?: return defaults
            val key = codeText.toIntOrNull() ?: return defaults
            if (bit !in defaults || !mayBind(key) || key in parsed.values) return defaults
            parsed[bit] = key
        }
        return if (parsed.keys == defaults.keys) parsed else defaults
    }
}

internal data class ConnectedPhysicalPad(val id: Int, val descriptor: String, val name: String)

internal data class PhysicalPadUiState(
    val devices: List<ConnectedPhysicalPad> = emptyList(),
    val p1Descriptor: String = "",
    val p2Descriptor: String = "",
    val p1Bindings: Map<Int, Int> = PhysicalPadMapping.defaults,
    val p2Bindings: Map<Int, Int> = PhysicalPadMapping.defaults,
    val capturingPlayer: Int = 0,
    val capturingBit: Int = -1
)
