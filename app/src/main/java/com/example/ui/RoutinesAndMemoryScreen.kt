package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.omega.TimeAutomationScheduler
import org.json.JSONArray
import org.json.JSONObject
import com.example.data.LocationReminder
import com.example.data.MemoryFact
import com.example.data.OmegaSettings
import com.example.data.VoiceRoutine
import com.example.ui.theme.OmegaColors
import com.example.ui.theme.OmegaLiquidBackdrop
import com.example.ui.theme.accentColor
import com.example.ui.theme.mutedText
import com.example.ui.theme.omegaGlassCard
import com.example.ui.theme.omegaSwitchColors
import com.example.ui.theme.primaryText

@Composable
fun RoutinesAndMemoryScreen(
    settings: OmegaSettings,
    routines: List<VoiceRoutine>,
    memories: List<MemoryFact>,
    locationReminders: List<LocationReminder>,
    onBack: () -> Unit,
    onUpdateSettings: ((OmegaSettings) -> OmegaSettings) -> Unit,
    onTestTrigger: (String) -> Unit,
    onScheduleRoutine: (String, Int, Int) -> Unit,
    onCancelSchedule: (String) -> Unit,
    scheduleMessage: String?,
    onRunRoutine: (VoiceRoutine) -> Unit,
    onAddRoutine: (String, String, String, String) -> Unit,
    onDeleteRoutine: (Long) -> Unit,
    onDeleteMemory: (String) -> Unit
) {
    val theme = settings.visualTheme
    val preset = settings.orbColorPreset
    val accent = theme.accentColor(preset)
    var showCreate by remember { mutableStateOf(false) }
    var scheduleTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    OmegaLiquidBackdrop(theme = theme, preset = preset) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                GlassCircleButton(Icons.AutoMirrored.Rounded.ArrowBack, theme = theme, preset = preset, onClick = onBack)
                Text("Triggers, Routines & Memory", color = theme.primaryText(), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                GlassCircleButton(Icons.Rounded.Add, theme = theme, preset = preset, onClick = { showCreate = true })
            }

            Spacer(modifier = Modifier.height(14.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().omegaGlassCard(22.dp, theme, preset).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Bolt, null, tint = accent, modifier = Modifier.size(20.dp))
                            Text("Smart Auto-Triggers", color = theme.primaryText(), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        TriggerRow(theme, preset, "Bluetooth / Earbuds Connect", "Hands-free unread notification briefing", settings.triggerBluetoothBriefing,
                            { on -> onUpdateSettings { it.copy(triggerBluetoothBriefing = on) } }, { onTestTrigger("bluetooth") })
                        TriggerRow(theme, preset, "Low Battery (<15%) Auto-Saver", "Auto flashlight OFF + media volume 20%", settings.triggerLowBatterySaver,
                            { on -> onUpdateSettings { it.copy(triggerLowBatterySaver = on) } }, { onTestTrigger("low_battery") })
                        TriggerRow(theme, preset, "Charger Connect Alert", "Announce live battery % on plug-in", settings.triggerChargingAlert,
                            { on -> onUpdateSettings { it.copy(triggerChargingAlert = on) } }, { onTestTrigger("charging") })
                    }
                }

                item { Text("Voice Routines & Daily Schedule", color = theme.primaryText(), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (!scheduleMessage.isNullOrBlank()) item { Text(scheduleMessage, color = theme.primaryText(), fontSize = 13.sp) }
                items(routines, key = { it.id }) { routine ->
                    Row(
                        modifier = Modifier.fillMaxWidth().omegaGlassCard(20.dp, theme, preset).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(routine.name, color = theme.primaryText(), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Say: \"${routine.triggerPhrase}\"", color = accent, fontSize = 12.sp)
                            Text(routine.actionsSummary, color = theme.mutedText(), fontSize = 12.sp)
                            TimeAutomationScheduler.status(context, routine.name)?.let { Text(it, color = accent, fontSize = 11.sp) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { scheduleTarget = routine.name }) {
                                Icon(Icons.Rounded.Schedule, "Configure daily schedule", tint = theme.mutedText(), modifier = Modifier.size(18.dp))
                            }
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(accent).clickable { onRunRoutine(routine) }.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) { Text("Run", color = Color(0xFF060A16), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            IconButton(onClick = { onDeleteRoutine(routine.id) }) {
                                Icon(Icons.Rounded.DeleteOutline, "Delete", tint = theme.mutedText(), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                if (locationReminders.isNotEmpty()) {
                    item { Text("Location Reminders", color = theme.primaryText(), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    items(locationReminders, key = { it.id }) { rem ->
                        Row(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp, theme, preset).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.LocationOn, null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(rem.placeLabel, color = theme.primaryText(), fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                                Text(rem.reminderText, color = theme.mutedText(), fontSize = 12.sp)
                            }
                        }
                    }
                }

                item { Text("Encrypted Personal Memory", color = theme.primaryText(), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (memories.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp, theme, preset).padding(16.dp)) {
                            Text("No encrypted memories yet. Tell JARVIS: \"Remember my favorite music is Telugu lo-fi.\"", color = theme.mutedText(), fontSize = 13.sp)
                        }
                    }
                } else {
                    items(memories, key = { it.id }) { fact ->
                        Row(
                            modifier = Modifier.fillMaxWidth().omegaGlassCard(18.dp, theme, preset).padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fact.key, color = accent, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                Text(fact.value, color = theme.primaryText(), fontSize = 14.sp)
                            }
                            IconButton(onClick = { onDeleteMemory(fact.key) }) {
                                Icon(Icons.Rounded.Close, "Forget", tint = theme.mutedText(), modifier = Modifier.size(18.dp))
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
        }, confirmButton = { Button(enabled = h != null && h in 0..23 && m != null && m in 0..59, onClick = { if (h != null && m != null) onScheduleRoutine(name,h,m); scheduleTarget = null }) { Text("Save schedule") } },
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
            Text(title, color = theme.primaryText(), fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            Text(sub, color = theme.mutedText(), fontSize = 11.5.sp)
        }
        TextButton(onClick = onTest, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text("Test", color = theme.accentColor(preset), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Switch(checked = checked, onCheckedChange = onChecked, colors = omegaSwitchColors(theme, preset))
    }
}
