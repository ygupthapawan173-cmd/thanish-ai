package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.PortableDataBackup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PortableBackupPanel() {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }
    val exportPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if(uri!=null && !busy && password.length in 12..256 && password==confirmation) {
            val secret=password.toCharArray(); password=""; confirmation=""; busy=true
            scope.launch {
                try { status="Encrypted ${PortableDataBackup.export(context,uri,secret)} rows to the selected file." }
                catch(e:CancellationException){throw e} catch(_:Exception){status="Export failed. Check storage, backup size and database access."}
                finally {secret.fill('\u0000'); busy=false}
            }
        }
    }
    val restorePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null && !busy && password.length in 12..256) {
            val secret=password.toCharArray(); password=""; confirmation=""; busy=true
            scope.launch {
                try { status=PortableDataBackup.restore(context,uri,secret) }
                catch(e:CancellationException){throw e} catch(_:Exception){status="Restore failed. Check password, archive integrity and database access. No partial merge."}
                finally {secret.fill('\u0000'); busy=false}
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Portable encrypted data backup",style=MaterialTheme.typography.titleMedium)
        Text("Chat, memories, routines, reminders and audit history. Existing rows are never overwritten. Keys, passwords and schedule approvals are excluded.",style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(password,{if(it.length<=256) password=it},label={Text("Backup password (12+ characters)")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedTextField(confirmation,{if(it.length<=256) confirmation=it},label={Text("Confirm password for export")},visualTransformation=PasswordVisualTransformation(),singleLine=true,enabled=!busy,modifier=Modifier.fillMaxWidth())
        OutlinedButton(onClick={exportPicker.launch("JARVIS-INFINITY-data.jib")},enabled=!busy && password.length>=12 && password==confirmation,modifier=Modifier.fillMaxWidth()){Text("Export encrypted data file")}
        OutlinedButton(onClick={confirmRestore=true},enabled=!busy && password.length>=12,modifier=Modifier.fillMaxWidth()){Text("Restore from backup file")}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(status.isNotBlank()) Text(status,style=MaterialTheme.typography.bodySmall)
    }
    if(confirmRestore) AlertDialog(onDismissRequest={confirmRestore=false},title={Text("Merge backup data?")},text={Text("Existing rows are preserved. Imported routines are not automatically scheduled or executed.")},confirmButton={TextButton(onClick={confirmRestore=false;restorePicker.launch(arrayOf("*/*"))}){Text("Select backup")}},dismissButton={TextButton(onClick={confirmRestore=false}){Text("Cancel")}})
}
