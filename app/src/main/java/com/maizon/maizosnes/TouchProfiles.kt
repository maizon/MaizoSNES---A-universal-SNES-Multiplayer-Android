package com.maizon.maizosnes

import org.json.JSONObject

/**
 * Original MaizoSNES control presets informed by UX principles in the Android
 * ports of CTR, ARMSX1 and PPSSPP. No images/code/assets are copied.
 */
internal enum class TouchPreset(
    val label: String, val hint: String, val diamond: Boolean = false
) {
    VIRTUAL_GAMEPAD("VirtualGamePad Mobile", "Face ABXY superior direita, setas inferiores esquerdas, L/R e Start/Select no topo", true),
    MAIZO("Maizo Original", "Translúcido, clássico SNES"),
    CTR("CTR Racing", "Botões quadrados, compacto para corrida"),
    ARMS("ARMSX Compact", "Neon com bordas finas e amplo espaço livre"),
    PPSSPP("PPSSPP Comfort", "Botões redondos em azul e branco"),
    PLAYSTATION("PlayStation Modern", "△ ○ ✕ □ coloridos em losango, estilo DualSense", true),
    XBOX("Xbox Series", "A B X Y coloridos, estilo controle Xbox", true),
    SWITCH("Nintendo Switch", "ABXY monocromáticos, compacto e limpo", true),
    ARCADE("Arcade RGB", "Botões numerados com neon e disposição em losango", true),
    STEAM("Steam Deck", "Face buttons minimalistas e direcional tipo stick", true)
}

/** Only visual legends change; bit mappings remain the same SNES/libretro inputs. */
internal fun TouchPreset.faceLegend(bit: Int): String = when (this) {
    TouchPreset.PLAYSTATION -> when (bit) {
        1 -> "□" // SNES Y maps west
        9 -> "△" // SNES X maps north
        0 -> "✕" // SNES B maps south
        8 -> "○" // SNES A maps east
        else -> "?"
    }
    TouchPreset.XBOX -> when (bit) {
        1 -> "X"
        9 -> "Y"
        0 -> "A"
        8 -> "B"
        else -> "?"
    }
    TouchPreset.ARCADE -> when (bit) {
        1 -> "1"
        9 -> "2"
        0 -> "3"
        8 -> "4"
        else -> "?"
    }
    else -> when (bit) {
        1 -> "Y"
        9 -> "X"
        0 -> "B"
        8 -> "A"
        else -> "?"
    }
}

internal fun TouchPreset.shoulderLegend(bit: Int): String = when (this) {
    TouchPreset.PLAYSTATION -> if (bit == 10) "L1" else "R1"
    TouchPreset.XBOX -> if (bit == 10) "LB" else "RB"
    TouchPreset.STEAM -> if (bit == 10) "L1" else "R1"
    else -> if (bit == 10) "L" else "R"
}

internal fun TouchPreset.menuLegend(bit: Int): String = when (this) {
    TouchPreset.PLAYSTATION -> if (bit == 2) "SHARE" else "OPTIONS"
    TouchPreset.XBOX -> if (bit == 2) "VIEW" else "MENU"
    TouchPreset.STEAM -> if (bit == 2) "VIEW" else "MENU"
    else -> if (bit == 2) "SELECT" else "START"
}

internal data class ButtonShift(val x: Float = 0f, val y: Float = 0f) {
    fun sanitized() = copy(x = x.coerceIn(-0.30f, 0.30f), y = y.coerceIn(-0.30f, 0.30f))
}

internal fun TouchPreset.defaultLayout(): TouchLayout = when (this) {
    TouchPreset.VIRTUAL_GAMEPAD -> TouchLayout(preset = this,
        dpadX = 0f, dpadY = 0f, buttonsX = 0f, buttonsY = 0f,
        scale = 1f, opacity = 0.78f)
    TouchPreset.MAIZO -> TouchLayout(preset = this)
    TouchPreset.CTR -> TouchLayout(preset = this,
        dpadX = 0.055f, dpadY = 0.10f, buttonsX = 0.045f, buttonsY = 0.065f,
        scale = 0.85f, opacity = 0.78f)
    TouchPreset.ARMS -> TouchLayout(preset = this,
        dpadX = 0.02f, dpadY = 0.06f, buttonsX = 0.015f, buttonsY = 0.08f,
        scale = 0.78f, opacity = 0.48f)
    TouchPreset.PPSSPP -> TouchLayout(preset = this,
        dpadX = 0.045f, dpadY = 0.035f, buttonsX = 0.04f, buttonsY = 0.04f,
        scale = 1.12f, opacity = 0.63f)
    TouchPreset.PLAYSTATION -> TouchLayout(preset = this,
        dpadX = 0.052f, dpadY = 0.055f, buttonsX = 0.052f, buttonsY = 0.045f,
        scale = 1.06f, opacity = 0.76f)
    TouchPreset.XBOX -> TouchLayout(preset = this,
        dpadX = 0.043f, dpadY = 0.075f, buttonsX = 0.060f, buttonsY = 0.04f,
        scale = 1.02f, opacity = 0.81f)
    TouchPreset.SWITCH -> TouchLayout(preset = this,
        dpadX = 0.040f, dpadY = 0.055f, buttonsX = 0.035f, buttonsY = 0.080f,
        scale = 0.90f, opacity = 0.68f)
    TouchPreset.ARCADE -> TouchLayout(preset = this,
        dpadX = 0.055f, dpadY = 0.025f, buttonsX = 0.028f, buttonsY = 0.035f,
        scale = 1.16f, opacity = 0.85f)
    TouchPreset.STEAM -> TouchLayout(preset = this,
        dpadX = 0.035f, dpadY = 0.055f, buttonsX = 0.038f, buttonsY = 0.055f,
        scale = 1.02f, opacity = 0.77f)
}

/** Portable persistence with per-button fractional offsets (screen-normalized). */
internal object TouchLayoutCodec {
    fun encode(layout: TouchLayout): String {
        val root = JSONObject()
            .put("preset", layout.preset.name)
            .put("enabled", layout.enabled)
            .put("dpadX", layout.dpadX.toDouble())
            .put("dpadY", layout.dpadY.toDouble())
            .put("buttonsX", layout.buttonsX.toDouble())
            .put("buttonsY", layout.buttonsY.toDouble())
            .put("scale", layout.scale.toDouble())
            .put("opacity", layout.opacity.toDouble())
        val offsets = JSONObject()
        layout.buttonShifts.forEach { (id, shift) ->
            if (id in setOf(0, 1, 2, 3, 8, 9, 10, 11)) {
                offsets.put(id.toString(), JSONObject()
                    .put("x", shift.x.toDouble()).put("y", shift.y.toDouble()))
            }
        }
        root.put("buttons", offsets)
        return root.toString()
    }

    fun decode(text: String?, expectedPreset: TouchPreset): TouchLayout? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val obj = JSONObject(text)
            val offsets = obj.optJSONObject("buttons")
            val result = mutableMapOf<Int, ButtonShift>()
            if (offsets != null) {
                val keys = offsets.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toIntOrNull() ?: continue
                    if (id !in setOf(0, 1, 2, 3, 8, 9, 10, 11)) continue
                    val v = offsets.optJSONObject(key) ?: continue
                    result[id] = ButtonShift(v.optDouble("x", 0.0).toFloat(),
                        v.optDouble("y", 0.0).toFloat()).sanitized()
                }
            }
            val original = expectedPreset.defaultLayout()
            original.copy(
                enabled = obj.optBoolean("enabled", true),
                dpadX = obj.optDouble("dpadX", original.dpadX.toDouble()).toFloat().coerceIn(0f, 0.65f),
                dpadY = obj.optDouble("dpadY", original.dpadY.toDouble()).toFloat().coerceIn(0f, 0.65f),
                buttonsX = obj.optDouble("buttonsX", original.buttonsX.toDouble()).toFloat().coerceIn(0f, 0.65f),
                buttonsY = obj.optDouble("buttonsY", original.buttonsY.toDouble()).toFloat().coerceIn(0f, 0.65f),
                scale = obj.optDouble("scale", original.scale.toDouble()).toFloat().coerceIn(0.70f, 1.45f),
                opacity = obj.optDouble("opacity", original.opacity.toDouble()).toFloat().coerceIn(0.20f, 0.90f),
                buttonShifts = result
            )
        }.getOrNull()
    }
}
