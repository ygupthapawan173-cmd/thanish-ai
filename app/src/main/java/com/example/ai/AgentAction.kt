package com.example.ai

import org.json.JSONObject

/**
 * An AI-proposed action. It is a proposal only; the UI must obtain user approval
 * before the accessibility service executes it.
 */
data class AgentAction(
    val name: String,
    val args: JSONObject
)
