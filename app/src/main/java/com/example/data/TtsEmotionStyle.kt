package com.example.data

enum class TtsEmotionStyle(val label: String, val speedMul: Float, val pitchMul: Float) {
    NATURAL("Natural", 1.0f, 1.0f),
    WARM("Warm", 0.94f, 0.96f),
    CRISP("Crisp Pro", 1.08f, 1.04f),
    CALM("Calm", 0.88f, 0.92f),
    DEEP("Deep JARVIS (pitch)", 0.92f, 0.78f),
    COMMANDER("Commander", 1.02f, 0.86f),
    GENTLE("Gentle", 0.90f, 1.06f),
    BRIGHT("Bright", 1.08f, 1.15f),
    NARRATOR("Narrator", 0.92f, 0.90f),
    QUICK("Quick", 1.22f, 1.0f),
    SOFT("Soft", 0.84f, 1.02f);

    fun speed(base: Float) = (base * speedMul).takeIf { it.isFinite() }?.coerceIn(0.5f, 1.8f) ?: 1f
    fun pitch(base: Float) = (base * pitchMul).takeIf { it.isFinite() }?.coerceIn(0.5f, 1.6f) ?: 1f
}

