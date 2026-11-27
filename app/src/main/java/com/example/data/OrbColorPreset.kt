package com.example.data

enum class OrbColorPreset(val label: String, val primaryHex: Long, val secondaryHex: Long, val accentHex: Long, val backgroundHex: Long, val surfaceHex: Long) {
    SOLAR_NOVA("Solar Nova", 0xFFFF7845, 0xFFFF4081, 0xFFA855F7, 0xFF0C0609, 0xFF301721),
    AURORA_CYAN("Obsidian Infinity", 0xFF00E5FF, 0xFF2563FF, 0xFF8B5CF6, 0xFF080E1D, 0xFF12233F),
    ROYAL_EMERALD("Emerald Matrix", 0xFF00E5A0, 0xFF00A87D, 0xFFB0FFE3, 0xFF020C09, 0xFF103128),
    CRIMSON_NOVA("Crimson Eclipse", 0xFFFF3158, 0xFFFF7060, 0xFFA50039, 0xFF100508, 0xFF30131D),
    NEBULA_VIOLET("Quantum Violet", 0xFFA855F7, 0xFFDDC4FF, 0xFF6344FF, 0xFF090511, 0xFF211439),
    ARCTIC_ICE("Arctic Frost", 0xFFFFFFFF, 0xFFA8DFFF, 0xFF609FBA, 0xFF071017, 0xFF1B303D),
    MIDNIGHT_SAPPHIRE("Midnight Sapphire", 0xFF2266FF, 0xFF64B5FF, 0xFFE0F0FF, 0xFF030916, 0xFF122442),
    TITANIUM_GOLD("Titanium Gold", 0xFFD4AF37, 0xFFFFE09A, 0xFFFFF4D4, 0xFF0A0906, 0xFF292419),
    AURORA_SPECTRUM("Aurora Spectrum", 0xFF20E3B2, 0xFF648BFF, 0xFFCB65FF, 0xFF070A16, 0xFF19243D),
    ROSE_PLATINUM("Rose Platinum", 0xFFFF7EBD, 0xFFE2AFEA, 0xFFFFE6FA, 0xFF10080F, 0xFF30202E),
    CARBON_SILVER("Carbon Silver", 0xFFFFFFFF, 0xFF8798B0, 0xFFC9D2DE, 0xFF08090B, 0xFF24262D);

    companion object {
        fun fromStored(value: String?): OrbColorPreset = entries.firstOrNull { it.name == value } ?: AURORA_CYAN
    }
}
