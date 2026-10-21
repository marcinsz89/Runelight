package com.moonbench.bifrost.animations

enum class LedAnimationType(
    val needsColorSelection: Boolean,
    val supportsSpeed: Boolean
) {
    AMBIENT(false, true),
    STATIC(true, false),
    BREATH(true, true),
    RAINBOW(false, true),
    PULSE(true, true);

    companion object {
        private val LEGACY_NAMES = mapOf("AMBILIGHT" to AMBIENT)

        fun fromStoredName(value: String?): LedAnimationType? {
            if (value.isNullOrBlank()) return null
            return runCatching { valueOf(value) }.getOrNull() ?: LEGACY_NAMES[value.uppercase()]
        }
    }
}
