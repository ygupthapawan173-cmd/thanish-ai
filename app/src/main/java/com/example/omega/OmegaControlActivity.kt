package com.example.omega

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.*
import com.example.ui.theme.PanduBabuTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class OmegaControlActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PanduBabuTheme { OmegaCenter { finish() } } }
    }
}
@Composable
fun OmegaCenter(embedded: Boolean = false, close: () -> Unit = {}) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    val wakeStatus by OmegaWakeService.status.collectAsState()
    val wakeActive by OmegaWakeService.active.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(OmegaVoicePreferences.get(c)) }
    var notice by remember { mutableStateOf("") }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        notice = if (it.values.all { granted -> granted }) "Permissions granted. Tap Start." else "Permission denied; microphone remains stopped."
    }
    val notebook = remember { OmegaNotebook(c) }
    var entries by remember { mutableStateOf(emptyList<OmegaEntry>()) }
    var ready by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var editId by remember { mutableStateOf<String?>(null) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var diagnostic by remember { mutableStateOf("") }
    // AI provider configuration remains in the existing main Settings connection panel.

    fun operate(op: () -> List<OmegaEntry>) {
        if (busy) return
        busy = true
        scope.launch {
            try { entries = withContext(Dispatchers.IO) { op() }; ready = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { notice = "Vault operation failed. Existing data was not reset." }
            finally { busy = false }
        }
    }
    fun measure() {
        val memory = ActivityManager.MemoryInfo().also { c.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val battery = c.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(c)
        diagnostic = "Android API " + Build.VERSION.SDK_INT + "\nBattery: " +
            (if (battery in 0..100) "$battery%" else "unavailable") + "\nAvailable RAM: " +
            memory.availMem / 1048576 + " MB\nFree app volume: " + StatFs(c.filesDir.path).availableBytes / 1048576 +
            " MB\nOn-device speech service: " + local + "\nWake: " + wakeStatus
    }
    LaunchedEffect(Unit) { operate { notebook.list() }; measure() }
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("OMEGA / NATIVE", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Premium offline and online voice control")
                }
                if (!embedded) TextButton(onClick = close) { Text("Close") }
            }
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp) {
                listOf("Voice", "Memory", "System").forEachIndexed { i, label ->
                    Tab(selected = i == tab, onClick = { tab = i }, text = { Text(label) })
                }
            }
            if (notice.isNotBlank()) Text(notice, color = MaterialTheme.colorScheme.secondary)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        Text("OMEGA • Wake & Voice", style = MaterialTheme.typography.titleLarge)
                        Text("On-device language packs depend on Android. Prefer on-device uses the platform service if local recognition is unavailable.")
                        OmegaVoiceMode.entries.forEach { item -> Row {
                            RadioButton(selected = item == mode, onClick = { mode = item; OmegaVoicePreferences.set(c, item) })
                            Text(item.label, modifier = Modifier.padding(top = 14.dp))
                        } }
                        HorizontalDivider()
                        Text("Offline Hey JARVIS • on-device Vosk")
                        Text(wakeStatus)
                        Text("Bundled English model. Say Hey JARVIS then one English command. Tap the notification and review the transcript. No wake audio is uploaded.")
                        Button(enabled = !wakeActive, modifier = Modifier.fillMaxWidth(), onClick = {
                            val wanted = buildList { add(Manifest.permission.RECORD_AUDIO); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
                            val missing = wanted.filter { ContextCompat.checkSelfPermission(c, it) != PackageManager.PERMISSION_GRANTED }
                            if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
                            else try { OmegaWakeService.start(c) } catch (e: Exception) { notice = "Wake service could not start." }
                        }) { Text("Start offline listening") }
                        OutlinedButton(onClick = { OmegaWakeService.stop(c) }, modifier = Modifier.fillMaxWidth()) { Text("STOP microphone") }
                        Text("Locked-screen endurance needs device testing.")
                    }
                    1 -> {
                        Text("Encrypted notebook & tasks", style = MaterialTheme.typography.titleLarge)
                        Text("Local, opt-in memory; separate from chat history. Maximum 80 entries.")
                        OutlinedTextField(note, { note = it.take(1000) }, label = { Text("Note / task") }, modifier = Modifier.fillMaxWidth())
                        if (editId == null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = ready && !busy && note.isNotBlank(), onClick = { val value = note; operate { notebook.add(value, false) }; note = "" }) { Text("Save note") }
                            OutlinedButton(enabled = ready && !busy && note.isNotBlank(), onClick = { val value = note; operate { notebook.add(value, true) }; note = "" }) { Text("Add task") }
                        } else {
                            Button(enabled = ready && !busy && note.isNotBlank(), onClick = { val id = editId; val value = note; if (id != null) operate { notebook.edit(id, value) }; editId = null; note = "" }) { Text("Save edit") }
                            TextButton(onClick = { editId = null; note = "" }) { Text("Cancel") }
                        }
                        OutlinedTextField(query, { query = it.take(150) }, label = { Text("Search Telugu / English") }, modifier = Modifier.fillMaxWidth())
                        Text(entries.count { it.task && it.done }.toString() + "/" + entries.count { it.task } + " tasks complete")
                        entries.filter { OmegaPolicy.matches(it.text, query) }.forEach { e ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                                Text(if (e.task) if (e.done) "Task · done" else "Task · pending" else "Memory")
                                SelectionContainer { Text(e.text) }
                                Row {
                                    if (e.task) TextButton(enabled = !busy, onClick = { operate { notebook.toggle(e.id) } }) { Text(if (e.done) "Reopen" else "Done") }
                                    TextButton(onClick = { editId = e.id; note = e.text }) { Text("Edit") }
                                    TextButton(onClick = { deleteId = e.id }) { Text("Delete") }
                                }
                            } }
                        }
                        OutlinedButton(enabled = entries.isNotEmpty(), onClick = {
                            val text = entries.joinToString("\n\n") { (if (it.task) if (it.done) "[Done] " else "[Task] " else "[Note] ") + it.text }
                            c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share decrypted notes"))
                        }) { Text("Share decrypted notes") }
                    }
                    2 -> {
                        Text("OMEGA • System diagnostics", style = MaterialTheme.typography.titleLarge)
                        Text(diagnostic); Button(onClick = { measure() }) { Text("Refresh measurements") }
                        Text("Microphone permissions, accessibility controls and voice configuration remain under user control. AI connections are managed in one Settings panel.")
                    }
                }
            }
        }
    }
    if (deleteId != null) AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete this local entry?") },
        text = { Text("The selected entry will be removed from the encrypted notebook.") },
        confirmButton = { TextButton(onClick = { val id = deleteId; deleteId = null; if (id != null) operate { notebook.delete(id) } }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Cancel") } })
}

