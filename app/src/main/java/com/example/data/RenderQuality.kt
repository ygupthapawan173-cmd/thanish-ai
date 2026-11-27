package com.example.data

/** Particle density policy, not a display refresh rate guarantee. */
enum class RenderQuality(val label: String) {
    ADAPTIVE("Adaptive Smooth"), HIGH("High Detail"), SAVER("Battery Saver");
    fun reduced(powerSave: Boolean, lowBattery: Boolean, thermalSevere: Boolean = false): Boolean =
        this == SAVER || powerSave || thermalSevere || (this == ADAPTIVE && lowBattery)
    companion object {
        fun fromStored(value: String?): RenderQuality = entries.firstOrNull { it.name == value } ?: ADAPTIVE
    }
}
