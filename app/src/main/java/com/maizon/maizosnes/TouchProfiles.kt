package com.maizon.maizosnes

import org.json.JSONObject

/**
 * Original MaizoSNES control presets informed by UX principles in the Android
 * ports of CTR, ARMSX1 and PPSSPP. No images/code/assets are copied.
 */
internal enum class TouchPreset(val label: String, val hint: String) {
    MAIZO("Maizo Original", "Translúcido, D-pad na esquerda e ABXY na direita"),
    CTR("CTR Racing", "Controles baixos e compactos, útil para corrida"),
    ARMS("ARMSX Compact", "Maior área livre na imagem, bordas discretas"),
    PPSSPP("PPSSPP Comfort", "Áreas de toque maiores e mais afastadas")
}

internal data class ButtonShift(val x: Float = 0f, val y: Float = 0f) {
    fun sanitized() = copy(x = x.coerceIn(-0.30f, 0.30f), y = y.coerceIn(-0.30f, 0.30f))
}

internal fun TouchPreset.defaultLayout(): TouchLayout = when (this) {
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
