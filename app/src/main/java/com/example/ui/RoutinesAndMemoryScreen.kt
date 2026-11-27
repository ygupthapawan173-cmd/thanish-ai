package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.autonomous.ConditionalAutomationRule
import com.example.autonomous.ProactiveSuggestion
import com.example.autonomous.RoutineSuggestion
import com.example.data.LocationReminder
import com.example.data.MemoryFact
import com.example.data.OmegaSettings
import com.example.data.VoiceRoutine
import com.example.omega.TimeAutomationScheduler
import com.example.ui.theme.omegaSwitchColors
import org.json.JSONArray
import org.json.JSONObject

private val AutoNavyTop = Color(0xFF040916)
private val AutoNavyCard = Color(0xFF0E1B36)
private val AutoNeonCyan = Color(0xFF00F0FF)
private val AutoTextPrimary = Color(0xFFF8FAFC)
private val AutoTextMuted = Color(0xFF94A3B8)

@Composable
fun RoutinesAndMemoryScreen(
    settings: OmegaSettings,
    routines: List<VoiceRoutine>,
    memories: List<MemoryFact>,
    locationReminders: List<LocationReminder>,
    proactiveSuggestions: List<ProactiveSuggestion> = emptyList(),
    routineSuggestions: List<RoutineSuggestion> = emptyList(),
    conditionalRules: List<ConditionalAutomationRule> = emptyList(),
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit,
    onUpdateSettings: ((OmegaSettings) -> OmegaSettings) -> Unit,
    onTestTrigger: (String) -> Unit,
    onScheduleRoutine: (String, Int, Int) -> Unit,
    onCancelSchedule: (String) -> Unit,
    scheduleMessage: String?,
    onRunRoutine: (VoiceRoutine) -> Unit,
    onAddRoutine: (String, String, String, String) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onToggleConditionalRule: (String) -> Unit = {},
    onAcceptRoutineSuggestion: (RoutineSuggestion) -> Unit = {},
    onRunDailyPlan: () -> Unit = {}
) {
    val theme = settings.visualTheme
    val preset = settings.orbColorPreset
    var showCreate by remember { mutableStateOf(false) }
    var scheduleTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(AutoNavyTop, Color(0xFF071126))))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, theme = theme, preset = preset, onClick = onBack)
                Text("Assistant, Rules & Memory", color = AutoTextPrimary, fontSize = 16.5.sp, fontWeight = FontWeight.Bold)
                GlassCircleButton(Icons.Rounded.Add, theme = theme, preset = preset, onClick = { showCreate = true })
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Button(
                        onClick = onRunDailyPlan,
                        colors = ButtonDefaults.buttonColors(containerColor = AutoNeonCyan),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Today, null, tint = AutoNavyTop, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Generate Smart Daily Plan", color = AutoNavyTop, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (conditionalRules.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AutoNavyCard)
                                .border(1.dp, AutoNeonCyan.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Conditional IF–THEN Automation", color = AutoNeonCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            conditionalRules.forEach { rule ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(rule.condition.label, color = AutoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("THEN: ${rule.thenActionSummary}", color = AutoTextMuted, fontSize = 11.5.sp)
                                    }
                                    Switch(checked = rule.enabled, onCheckedChange = { onToggleConditionalRule(rule.id) }, colors = omegaSwitchColors(theme, preset))
                                }
                            }
                        }
                    }
                }

                if (routineSuggestions.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AutoNavyCard)
                                .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.35f), RoundedCornerShape(18.dp)).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Suggested Routines (From Your Habits)", color = AutoNeonCyan, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            routineSuggestions.forEach { sug ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(sug.suggestedName, color = AutoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Trigger: \"${sug.triggerPhrase}\" • ${sug.summary}", color = AutoTextMuted, fontSize = 11.5.sp)
                                    }
                                    TextButton(onClick = { onAcceptRoutineSuggestion(sug) }) { Text("Add", color = AutoNeonCyan, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AutoNavyCard)
                            .border(1.dp, AutoNeonCyan.copy(alpha = 0.25f), RoundedCornerShape(20.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Bolt, null, tint = AutoNeonCyan, modifier = Modifier.size(20.dp))
                            Text("Smart Auto-Triggers", color = AutoTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        TriggerRow(theme, preset, "Bluetooth / Earbuds Connect", "Hands-free unread notification briefing", settings.triggerBluetoothBriefing,
                            { on -> onUpdateSettings { it.copy(triggerBluetoothBriefing = on) } }, { onTestTrigger("bluetooth") })
                        TriggerRow(theme, preset, "Low Battery (<15%) Auto-Saver", "Auto flashlight OFF + media volume 20%", settings.triggerLowBatterySaver,
                            { on -> onUpdateSettings { it.copy(triggerLowBatterySaver = on) } }, { onTestTrigger("low_battery") })
                        TriggerRow(theme, preset, "Charger Connect Alert", "Announce live battery % on plug-in", settings.triggerChargingAlert,
                            { on -> onUpdateSettings { it.copy(triggerChargingAlert = on) } }, { onTestTrigger("charging") })
                    }
                }

                item { Text("Voice Routines & Daily Schedule", color = AutoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (!scheduleMessage.isNullOrBlank()) item { Text(scheduleMessage, color = AutoNeonCyan, fontSize = 13.sp) }
                items(routines, key = { it.id }) { routine ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AutoNavyCard)
                            .border(1.dp, AutoNeonCyan.copy(alpha = 0.22f), RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(routine.name, color = AutoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Say: \"${routine.triggerPhrase}\"", color = AutoNeonCyan, fontSize = 12.sp)
                            Text(routine.actionsSummary, color = AutoTextMuted, fontSize = 12.sp)
                            TimeAutomationScheduler.status(context, routine.name)?.let { Text(it, color = AutoNeonCyan, fontSize = 11.sp) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scheduleTarget = routine.name }) {
                                Icon(Icons.Rounded.Schedule, "Configure daily schedule", tint = AutoTextMuted, modifier = Modifier.size(18.dp))
                            }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(AutoNeonCyan).clickable { onRunRoutine(routine) }.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) { Text("Run", color = AutoNavyTop, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            IconButton(onClick = { onDeleteRoutine(routine.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = AutoTextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (locationReminders.isNotEmpty()) {
                    item { Text("Intelligent Reminders", color = AutoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    items(locationReminders, key = { it.id }) { rem ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AutoNavyCard)
                                .border(1.dp, AutoNeonCyan.copy(alpha = 0.22f), RoundedCornerShape(18.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.LocationOn, null, tint = AutoNeonCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(rem.placeLabel, color = AutoTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Text(rem.reminderText, color = AutoTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                item { Text("Encrypted Personal Memory", color = AutoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (memories.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AutoNavyCard)
                                .border(1.dp, AutoNeonCyan.copy(alpha = 0.22f), RoundedCornerShape(18.dp)).padding(16.dp)
                        ) {
                            Text("No encrypted memories yet. Tell JARVIS: \"Remember my favorite music is Telugu lo-fi.\"", color = AutoTextMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(memories, key = { it.id }) { fact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AutoNavyCard)
                                .border(1.dp, AutoNeonCyan.copy(alpha = 0.22f), RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fact.key, color = AutoNeonCyan, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(fact.value, color = AutoTextPrimary, fontSize = 14.sp)
                            }
                            IconButton(onClick = { onDeleteMemory(fact.key) }) {
                                Icon(Icons.Rounded.Close, "Forget", tint = AutoTextMuted, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    scheduleTarget?.let { name ->
        var hour by remember(name) { mutableStateOf("07") }
        var minute by remember(name) { mutableStateOf("30") }
        val h = hour.toIntOrNull(); val m = minute.toIntOrNull()
        AlertDialog(onDismissRequest = { scheduleTarget = null }, title = { Text("Daily schedule · $name") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Local time. Inexact alarm; only flashlight and media volume run in background.")
                OutlinedTextField(hour, { hour = it.filter(Char::isDigit).take(2) }, label = { Text("Hour (0–23)") }, singleLine = true)
                OutlinedTextField(minute, { minute = it.filter(Char::isDigit).take(2) }, label = { Text("Minute (0–59)") }, singleLine = true)
                TextButton(onClick = { onCancelSchedule(name); scheduleTarget = null }) { Text("Cancel existing schedule") }
            }
        }, confirmButton = { Button(enabled = h != null && h in 0..23 && m != null && m in 0..59, onClick = { if (h != null && m != null) onScheduleRoutine(name, h, m); scheduleTarget = null }) { Text("Save schedule") } },
            dismissButton = { TextButton(onClick = { scheduleTarget = null }) { Text("Close") } })
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf("") }
        var appToOpen by remember { mutableStateOf("YouTube") }
        var vol by remember { mutableStateOf("75") }

        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Routine") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(trigger, { trigger = it }, label = { Text("Voice Trigger") }, singleLine = true)
                    OutlinedTextField(appToOpen, { appToOpen = it }, label = { Text("App to Open (optional)") }, singleLine = true)
                    OutlinedTextField(vol, { vol = it.filter { c -> c.isDigit() } }, label = { Text("Media Volume %") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val v = vol.toIntOrNull()?.coerceIn(0, 100) ?: 70
                    val safeApp = appToOpen.trim()
                    val steps = JSONArray().put(JSONObject().put("tool", "set_media_volume").put("args", JSONObject().put("percent", v)))
                    if (safeApp.isNotBlank()) steps.put(JSONObject().put("tool", "open_app").put("args", JSONObject().put("app_name", safeApp)))
                    onAddRoutine(name.ifBlank { "Quick Launch" }, trigger.ifBlank { "quick launch" }, "Vol $v%" + if (safeApp.isNotBlank()) " • Open $safeApp" else "", steps.toString())
                    showCreate = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TriggerRow(
    theme: com.example.data.OmegaVisualTheme,
    preset: com.example.data.OrbColorPreset,
    title: String,
    sub: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    onTest: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
            Text(title, color = AutoTextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = AutoTextMuted, fontSize = 11.5.sp)
        }
        TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Test", color = AutoNeonCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Switch(checked = checked, onCheckedChange = onChecked, colors = omegaSwitchColors(theme, preset))
    }
}
