package com.example.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One AI connection configuration surface, not a grid of provider slots.
 * Existing encrypted keys/models and provider fallback logic remain unchanged.
 */
@Composable
fun AiProviderSettingsCard() {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { EncryptedApiKeyStore(context) }
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(store.getActiveProvider()) }
    var active by remember { mutableStateOf(store.getActiveProvider()) }
    var expanded by remember { mutableStateOf(false) }
    var hasKey by remember { mutableStateOf(store.hasApiKey(provider)) }
    var model by remember { mutableStateOf(store.getModel(provider)) }
    var keyInput by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("OMEGA • AI CONNECTION", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(
                "One secure connection panel. Previously saved keys remain encrypted on this device. " +
                    "Only your chosen AI connection is shown here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AI service: ${provider.title}  ▾")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    AiProvider.entries.forEach { choice ->
                        DropdownMenuItem(
                            text = { Text(choice.title) },
                            onClick = {
                                provider = choice
                                hasKey = store.hasApiKey(choice)
                                model = store.getModel(choice)
                                keyInput = ""
                                status = ""
                                expanded = false
                            }
                        )
                    }
                }
            }
            Text(
                if (hasKey) "Encrypted key stored • " +
                    (if (active == provider) "Selected, not yet verified" else "Not selected")
                else "No key saved for this service",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasKey) Color(0xFF27D1AC) else MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model ID") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii)
            )
            if (provider == AiProvider.GEMINI) {
                Text("Model ID example: gemini-3.6-flash. Check the model is available in your AI Studio project.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it.take(1000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = {
                    Text(if (hasKey) "Stored securely — leave blank to reuse" else "Enter API key")
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false
                )
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && (hasKey || keyInput.isNotBlank()),
                onClick = {
                    val candidate = provider
                    val secret = keyInput.trim()
                    val newModel = model.trim().ifBlank { candidate.defaultModel }
                    busy = true
                    status = "Checking live connection…"
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) {
                                val key = secret.ifBlank {
                                    store.getApiKey(candidate) ?: error("Enter a key to connect")
                                }
                                val verified = PanduAiRepository(context)
                                    .testProvider(candidate, key, newModel)
                                // Commit credentials and model only AFTER the real API test succeeds.
                                if (secret.isNotBlank()) {
                                    check(store.saveApiKey(candidate, secret)) { "Secure save failed" }
                                }
                                store.setModel(candidate, newModel)
                                verified
                            }
                            if (provider == candidate) {
                                hasKey = true
                                keyInput = ""
                                model = newModel
                                status = result
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (provider == candidate) {
                                hasKey = store.hasApiKey(candidate)
                                status = (e as? AiConnectionTestException)?.message
                                    ?: "Connection could not be verified. Check device network and retry."
                            }
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text(if (busy) "Testing…" else "Save & Test Connection") }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    enabled = hasKey && !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        store.setActiveProvider(provider)
                        active = provider
                        status = "AI service selected."
                    }
                ) { Text(if (active == provider) "Selected" else "Use AI") }
                OutlinedButton(
                    enabled = hasKey && !busy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val toDelete = provider
                        scope.launch {
                            withContext(Dispatchers.IO) { store.deleteApiKey(toDelete) }
                            if (provider == toDelete) {
                                hasKey = false
                                keyInput = ""
                                status = "Saved key deleted from this phone."
                                if (active == toDelete) {
                                    val fallback = AiProvider.entries.firstOrNull { store.hasApiKey(it) }
                                        ?: AiProvider.GEMINI
                                    store.setActiveProvider(fallback)
                                    active = fallback
                                }
                            }
                        }
                    }
                ) { Text("Remove Key") }
            }
            if (status.isNotEmpty()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Saved/Selected does not mean Connected. Use Save & Test to verify the actual API. " +
                    "Other encrypted providers remain available for fallback. Cloud usage may incur charges.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
