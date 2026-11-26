package com.example.ai

import android.content.Context

/** Stores only the user's selected style, never AI keys or conversation text. */
object AiReplyPreferences {
    private const val PREFS = "omega_ai_reply_style"
    fun get(context: Context): ReplyStyle = runCatching {
        ReplyStyle.valueOf(context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("style", ReplyStyle.BALANCED.name)!!)
    }.getOrDefault(ReplyStyle.BALANCED)

    fun set(context: Context, style: ReplyStyle) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString("style", style.name).apply()
    }
}
