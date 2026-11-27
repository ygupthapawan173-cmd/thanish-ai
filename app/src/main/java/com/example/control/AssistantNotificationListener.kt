package com.example.control

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReplyActionHandle(val pendingIntent: PendingIntent, val remoteInput: RemoteInput)

data class CapturedNotification(
    val key: String,
    val appName: String,
    val title: String,
    val body: String,
    val timeLabel: String,
    val canDirectReply: Boolean = false
)

data class PendingNotificationReply(
    val senderOrApp: String,
    val resolvedTitle: String,
    val appName: String,
    val replyText: String
)

object NotificationInbox {
    private val _items = MutableStateFlow<List<CapturedNotification>>(emptyList())
    val items: StateFlow<List<CapturedNotification>> = _items.asStateFlow()

    private val _pendingReply = MutableStateFlow<PendingNotificationReply?>(null)
    val pendingReply: StateFlow<PendingNotificationReply?> = _pendingReply.asStateFlow()

    private val replyHandles = LinkedHashMap<String, ReplyActionHandle>()

    fun add(item: CapturedNotification, replyHandle: ReplyActionHandle? = null) {
        val current = _items.value.filterNot { it.key == item.key }
        _items.value = (listOf(item) + current).take(25)
        if (replyHandle != null) {
            replyHandles[item.key] = replyHandle
            if (replyHandles.size > 25) {
                replyHandles.keys.firstOrNull()?.let { replyHandles.remove(it) }
            }
        }
    }

    fun removeByKey(key: String) {
        _items.value = _items.value.filterNot { it.key == key }
        replyHandles.remove(key)
    }

    fun formatForAgent(limit: Int = 6): String {
        val recent = _items.value.take(limit)
        if (recent.isEmpty()) return "No unread notifications captured yet."
        return recent.joinToString("\n") { n ->
            val tag = if (n.canDirectReply) " [Replyable]" else ""
            "[${n.timeLabel}] ${n.appName} - ${n.title}: ${n.body}$tag"
        }
    }

    fun queueReplyForConfirmation(senderOrAppQuery: String, replyText: String): String {
        val q = senderOrAppQuery.trim().lowercase()
        val target = _items.value.firstOrNull { n ->
            n.canDirectReply && (n.title.lowercase().contains(q) || n.appName.lowercase().contains(q))
        } ?: return "No replyable notification found matching '$senderOrAppQuery'."

        _pendingReply.value = PendingNotificationReply(
            senderOrApp = senderOrAppQuery,
            resolvedTitle = target.title,
            appName = target.appName,
            replyText = replyText
        )
        return "Reply drafted for ${target.title} (${target.appName}): \"$replyText\". Waiting for your confirmation tap."
    }

    fun clearPendingReply() {
        _pendingReply.value = null
    }

    fun sendDirectReply(context: Context, senderOrAppQuery: String, replyText: String): String {
        val q = senderOrAppQuery.trim().lowercase()
        val target = _items.value.firstOrNull { n ->
            n.canDirectReply && (n.title.lowercase().contains(q) || n.appName.lowercase().contains(q))
        } ?: return "No replyable notification found matching '$senderOrAppQuery'."

        val handle = replyHandles[target.key] ?: return "Reply handle expired for ${target.title}."
        return try {
            val fillIntent = Intent()
            val bundle = Bundle().apply { putCharSequence(handle.remoteInput.resultKey, replyText) }
            RemoteInput.addResultsToIntent(arrayOf(handle.remoteInput), fillIntent, bundle)
            handle.pendingIntent.send(context, 0, fillIntent)
            _pendingReply.value = null
            "Sent reply to ${target.title} on ${target.appName}: \"$replyText\"."
        } catch (e: Exception) {
            "Direct reply failed: ${e.message}"
        }
    }
}

class AssistantNotificationListener : NotificationListenerService() {
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.isOngoing || sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val appLabel = runCatching {
            val info = packageManager.getApplicationInfo(sbn.packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(sbn.packageName)

        val handle = extractReplyAction(sbn.notification)
        NotificationInbox.add(
            CapturedNotification(
                key = sbn.key,
                appName = appLabel,
                title = title.ifBlank { appLabel },
                body = text,
                timeLabel = timeFormat.format(Date(sbn.postTime)),
                canDirectReply = handle != null
            ),
            replyHandle = handle
        )
    }

    private fun extractReplyAction(notification: Notification): ReplyActionHandle? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val firstInput = action.remoteInputs?.firstOrNull() ?: continue
            val pi = action.actionIntent ?: continue
            return ReplyActionHandle(pi, firstInput)
        }
        return null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn != null) NotificationInbox.removeByKey(sbn.key)
    }
}
