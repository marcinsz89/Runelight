package com.moonbench.bifrost.tools

import kotlin.math.roundToInt

/** Raw four-zone state. The controller serializes mutation, scaling and submission. */
internal class LedFrameState {
    data class Color(val red: Int, val green: Int, val blue: Int, val wireBrightness: Int = 255) {
        fun bounded() = Color(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255), wireBrightness.coerceIn(0, 255))
    }

    private val raw = arrayOfNulls<Color>(4)
    private val submitted = arrayOfNulls<Color>(4)
    var masterScale: Float = 1f
    var leftPercent: Int = 100
    var rightPercent: Int = 100

    fun update(left: Color, right: Color, selected: BooleanArray) {
        for (zone in raw.indices) if (selected[zone]) raw[zone] = (if (zone < 2) left else right).bounded()
    }

    fun blackBaseline() {
        for (zone in raw.indices) raw[zone] = raw[zone]?.copy(red = 0, green = 0, blue = 0)
    }

    private fun finalColors(): List<Color?> = raw.mapIndexed { zone, color ->
        val scale = masterScale.coerceIn(0f, 1f) * (if (zone < 2) leftPercent else rightPercent).coerceIn(0, 100) / 100f
        color?.copy(
            red = (color.red * scale).roundToInt(),
            green = (color.green * scale).roundToInt(),
            blue = (color.blue * scale).roundToInt()
        )
    }

    data class Submission(val command: String, val colors: List<Color?>)

    fun snapshot(): Submission? {
        val colors = finalColors()
        if (colors.all { it == null }) return null
        val commands = colors.mapIndexedNotNull { zone, color ->
            color?.let {
                val side = if (zone < 2) "l" else "r"
                val index = zone % 2 + 1
                "echo $index-${it.red}:${it.green}:${it.blue}:${it.wireBrightness} > /sys/class/sn3112$side/led/brightness"
            }
        }
        return Submission(commands.joinToString(" && "), colors)
    }

    fun pending(): Submission? = snapshot()?.takeUnless { next ->
        next.colors.indices.all { next.colors[it] == submitted[it] }
    }

    fun markSubmitted(submission: Submission) {
        submission.colors.forEachIndexed { index, color -> submitted[index] = color }
    }
}
