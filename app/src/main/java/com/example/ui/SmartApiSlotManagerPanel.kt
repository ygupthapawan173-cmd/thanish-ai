package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.ApiProviderKind
import com.example.ai.SlotConnectionState
import com.example.ai.SlotRoutingMode
import com.example.ai.SmartApiSlot
import com.example.ai.SmartApiSlotManager
import com.example.ui.theme.DeepBlue
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.IceWhite
import com.example.ui.theme.MidnightNavy
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.RoyalViolet
import java.util.Locale

/**
 * V110.1 Smart API Slot Manager Panel (Module 8, Targets 56-75):
 * Supports unlimited dynamic multi-provider slots, auto provider detection, Keystore encryption,
 * individual & batch live testing, HTTP 404/429/5xx cooldown status, separate Chat/Coding/TTS
 * routing, per-slot budgets/usage, and fastest/lowest-cost/free-first/priority selection.
 */
@Composable
internal fun SmartApiSlotManagerPanel(
    uiState: PanduUiState,
    onUpsertSlot: (SmartApiSlot) -> Unit,
    onToggleSlot: (String, Boolean) -> Unit,
    onDeleteSlot: (String) -> Unit,
    onSetRoutingMode: (SlotRoutingMode) -> Unit,
    onTestSlot: (String) -> Unit,
    onTestAllSlots: () -> Unit
) {
    var editingSlot by remember { mutableStateOf<SmartApiSlot?>(null) }
    var isNewSlot by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("SMART API SLOT MANAGER", color = NeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${uiState.apiSlots.count { it.enabled && it.apiKey.isNotBlank() }}/${uiState.apiSlots.size} configured • Keystore Encrypted",
                    color = Color(0xFF9FB4D0),
                    fontSize = 11.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onTestAllSlots,
                    enabled = !uiState.isTestingAllSlots && uiState.apiSlots.any { it.enabled && it.apiKey.isNotBlank() },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(if (uiState.isTestingAllSlots) "Testing..." else "Test All APIs", color = NeonCyan, fontSize = 11.sp)
                }
                Button(
                    onClick = {
                        isNewSlot = true
                        editingSlot = SmartApiSlot(
                            id = "",
                            label = "New API Slot",
                            providerKind = ApiProviderKind.OPENROUTER,
                            baseUrl = ApiProviderKind.OPENROUTER.defaultBaseUrl,
                            apiKey = "",
                            priority = (uiState.apiSlots.maxOfOrNull { it.priority } ?: 0) + 1
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("+ Add Slot", color = IceWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Priority / Cost / Speed Routing Strategy Selector (Target 64)
        Text("Slot Selection Priority", color = Color(0xFF9FB4D0), fontSize = 11.5.sp)
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SlotRoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = uiState.slotRoutingMode == mode,
                    onClick = { onSetRoutingMode(mode) },
                    label = { Text(mode.label, fontSize = 11.sp) }
                )
            }
        }

        uiState.apiSlots.forEach { slot ->
            val testOutcome = uiState.slotTestResults[slot.id]
            val chatState = slot.effectiveStatus()
            val statusColor = when (chatState) {
                SlotConnectionState.VERIFIED -> OmegaColors.EmeraldLive
                SlotConnectionState.COOLDOWN, SlotConnectionState.TESTING -> OmegaColors.AmberWarn
                SlotConnectionState.FAILED -> OmegaColors.CrimsonErr
                else -> Color(0xFF9FB4D0)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MidnightNavy.copy(alpha = 0.85f))
                    .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f).padding(end = 6.dp)) {
                        Text(slot.label, color = IceWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${slot.providerKind.displayName} • ${slot.maskedKey()} • P${slot.priority}",
                            color = Color(0xFF9FB4D0),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f))
                    ) {
                        Text(
                            chatState.label,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = slot.enabled, onCheckedChange = { onToggleSlot(slot.id, it) })
                }

                val routes = buildList {
                    if (slot.useForChat) add("Chat")
                    if (slot.useForCoding) add("Code")
                    if (slot.useForVoiceTts && slot.supportsTts) add("TTS:${slot.effectiveTtsStatus().label}")
                }.joinToString(" • ")

                Text(
                    "Model: ${slot.chatModel.ifBlank { "Auto" }} • Routes: $routes",
                    color = NeonCyan,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "Latency: ${slot.lastLatencyMs}ms • Reqs: ${slot.requestsToday} • Cost: $${String.format(Locale.US, "%.4f", slot.knownCostUsd)}${if (slot.budgetUsd > 0) " / $${slot.budgetUsd}" else if (slot.isFreeTier) " (Free Tier)" else ""}",
                    color = Color(0xFF9FB4D0),
                    fontSize = 10.5.sp
                )

                val detailText = testOutcome?.let {
                    val ttsPart = if (it.ttsOk != null) " | TTS: ${it.ttsDetail}" else ""
                    "${it.chatDetail}$ttsPart"
                } ?: slot.lastChatError.ifBlank { "" }
                if (detailText.isNotBlank()) {
                    Text(
                        detailText,
                        color = if (slot.lastChatError.isNotBlank() && chatState == SlotConnectionState.FAILED) Color(0xFFFDA4AF) else IceWhite.copy(alpha = 0.85f),
                        fontSize = 10.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onTestSlot(slot.id) },
                        enabled = slot.apiKey.isNotBlank() && slot.enabled && chatState != SlotConnectionState.TESTING,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) { Text("Test Slot", color = NeonCyan, fontSize = 11.sp) }
                    OutlinedButton(
                        onClick = { isNewSlot = false; editingSlot = slot },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) { Text("Edit", color = IceWhite, fontSize = 11.sp) }
                    if (slot.legacySourceId.isBlank()) {
                        OutlinedButton(
                            onClick = { onDeleteSlot(slot.id) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("Delete", color = OmegaColors.CrimsonErr, fontSize = 11.sp) }
                    }
                }
            }
        }
    }

    editingSlot?.let { target ->
        SmartSlotEditorDialog(
            initial = target,
            isNew = isNewSlot,
            onDismiss = { editingSlot = null },
            onSave = { updated ->
                onUpsertSlot(updated)
                editingSlot = null
            }
        )
    }
}

@Composable
private fun SmartSlotEditorDialog(
    initial: SmartApiSlot,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SmartApiSlot) -> Unit
) {
    var provider by remember { mutableStateOf(initial.providerKind) }
    var label by remember { mutableStateOf(initial.label) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var chatModel by remember { mutableStateOf(initial.chatModel) }
    var codingModel by remember { mutableStateOf(initial.codingModel) }
    var ttsModel by remember { mutableStateOf(initial.ttsModel) }
    var priorityStr by remember { mutableStateOf(initial.priority.toString()) }
    var budgetStr by remember { mutableStateOf(if (initial.budgetUsd == 0.0) "0" else initial.budgetUsd.toString()) }
    var isFreeTier by remember { mutableStateOf(initial.isFreeTier) }
    var useChat by remember { mutableStateOf(initial.useForChat) }
    var useCoding by remember { mutableStateOf(initial.useForCoding) }
    var useTts by remember { mutableStateOf(initial.useForVoiceTts) }
    var showKey by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepBlue,
        titleContentColor = IceWhite,
        textContentColor = IceWhite,
        title = { Text(if (isNew) "Add Smart API Slot" else "Edit ${initial.label}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Provider (auto-detected from key/URL)", color = NeonCyan, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ApiProviderKind.entries.forEach { kind ->
                        FilterChip(
                            selected = provider == kind,
                            onClick = {
                                provider = kind
                                if (baseUrl.isBlank() || baseUrl == initial.baseUrl || ApiProviderKind.entries.any { it.defaultBaseUrl == baseUrl }) {
                                    baseUrl = kind.defaultBaseUrl
                                }
                                if (chatModel.isBlank()) chatModel = kind.defaultChatModel
                                isFreeTier = kind == ApiProviderKind.GEMINI_DIRECT || kind == ApiProviderKind.OPENROUTER
                            },
                            label = { Text(kind.displayName, fontSize = 10.5.sp) }
                        )
                    }
                }
                OutlinedTextField(label, { label = it }, label = { Text("Slot label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { next ->
                        baseUrl = next
                        val detected = SmartApiSlotManager.detectProvider(apiKey, next)
                        if (detected != ApiProviderKind.CUSTOM_OPENAI) provider = detected
                    },
                    label = { Text("HTTPS Base URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { next ->
                        apiKey = next
                        val detected = SmartApiSlotManager.detectProvider(next, baseUrl)
                        if (detected != ApiProviderKind.CUSTOM_OPENAI && isNew) {
                            provider = detected
                            if (baseUrl.isBlank()) baseUrl = detected.defaultBaseUrl
                            if (chatModel.isBlank()) chatModel = detected.defaultChatModel
                        }
                    },
                    label = { Text("API Key (AES-256 Encrypted)") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = NeonCyan)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(chatModel, { chatModel = it }, label = { Text("Chat model") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(codingModel, { codingModel = it }, label = { Text("Coding model (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(priorityStr, { priorityStr = it.filter(Char::isDigit).take(2) }, label = { Text("Priority") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(budgetStr, { budgetStr = it.take(10) }, label = { Text("Budget USD") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = isFreeTier, onClick = { isFreeTier = !isFreeTier }, label = { Text("Free Tier") })
                    FilterChip(selected = useChat, onClick = { useChat = !useChat }, label = { Text("Chat") })
                    FilterChip(selected = useCoding, onClick = { useCoding = !useCoding }, label = { Text("Coding") })
                    FilterChip(selected = useTts, onClick = { useTts = !useTts }, label = { Text("Voice/TTS") })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val prio = priorityStr.toIntOrNull()?.coerceIn(1, 99) ?: initial.priority
                val bud = budgetStr.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                onSave(
                    initial.copy(
                        label = label.ifBlank { provider.displayName },
                        providerKind = provider,
                        baseUrl = baseUrl.trim().trimEnd('/').ifBlank { provider.defaultBaseUrl },
                        apiKey = apiKey.trim(),
                        chatModel = chatModel.trim().ifBlank { provider.defaultChatModel },
                        codingModel = codingModel.trim(),
                        ttsModel = ttsModel.trim().ifBlank { provider.defaultTtsModel.orEmpty() },
                        priority = prio,
                        budgetUsd = bud,
                        isFreeTier = isFreeTier,
                        useForChat = useChat,
                        useForCoding = useCoding,
                        useForVoiceTts = useTts
                    )
                )
            }) { Text("Save Slot") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = IceWhite) } }
    )
}
