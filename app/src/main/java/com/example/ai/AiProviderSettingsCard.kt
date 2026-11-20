package com.example.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiProviderSettingsCard() {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { EncryptedApiKeyStore(context) }
    val scope = rememberCoroutineScope()
    var activeProvider by remember { mutableStateOf(store.getActiveProvider()) }
    var configured by remember {
        mutableStateOf(AiProvider.values().associateWith { store.hasApiKey(it) })
    }
    var keyDrafts by remember {
        mutableStateOf(AiProvider.values().associateWith { "" })
    }
    var modelDrafts by remember {
        mutableStateOf(AiProvider.values().associateWith { store.getModel(it) })
    }
    var messages by remember { mutableStateOf(emptyMap<AiProvider, String>()) }
    var busyProvider by remember { mutableStateOf<AiProvider?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "AI INTELLIGENCE • 4 API SLOTS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Add keys, choose a model, test each connection, and select the active AI. Keys are encrypted on this phone using Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AiProvider.values().forEach { provider ->
                val isConfigured = configured[provider] == true
                val isActive = activeProvider == provider

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = provider.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = provider.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (isActive) "ACTIVE" else if (isConfigured) "SAVED" else "EMPTY",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) Color(0xFF00CFA5) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        OutlinedTextField(
                            value = modelDrafts[provider].orEmpty(),
                            onValueChange = { modelDrafts = modelDrafts + (provider to it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Model ID") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Ascii
                            )
                        )

                        OutlinedTextField(
                            value = keyDrafts[provider].orEmpty(),
                            onValueChange = { keyDrafts = keyDrafts + (provider to it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API key") },
                            placeholder = { Text(if (isConfigured) "Saved key hidden — leave blank to reuse" else "Paste API key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                keyboardType = KeyboardType.Password,
                                autoCorrectEnabled = false
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val draft = keyDrafts[provider].orEmpty().trim()
                                    val model = modelDrafts[provider].orEmpty().trim().ifBlank { provider.defaultModel }
                                    scope.launch {
                                        busyProvider = provider
                                        messages = messages - provider
                                        try {
                                            val message = withContext(Dispatchers.IO) {
                                                val key = if (draft.isNotBlank()) {
                                                    check(store.saveApiKey(provider, draft)) { "Could not securely save this key." }
                                                    draft
                                                } else {
                                                    store.getApiKey(provider)
                                                        ?: error("Enter an API key first.")
                                                }
                                                store.setModel(provider, model)
                                                PanduAiRepository(context).testProvider(provider, key, model)
                                            }
                                            configured = configured + (provider to true)
                                            modelDrafts = modelDrafts + (provider to model)
                                            keyDrafts = keyDrafts + (provider to "")
                                            messages = messages + (provider to message)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            configured = configured + (provider to withContext(Dispatchers.IO) {
                                                store.hasApiKey(provider)
                                            })
                                            messages = messages + (provider to (e.localizedMessage ?: "Connection test failed."))
                                        } finally {
                                            busyProvider = null
                                        }
                                    }
                                },
                                enabled = busyProvider == null,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Text(if (busyProvider == provider) "Testing…" else "Save & Test")
                            }

                            OutlinedButton(
                                onClick = {
                                    if (isConfigured) {
                                        store.setActiveProvider(provider)
                                        activeProvider = provider
                                        messages = messages + (provider to "Active provider changed.")
                                    }
                                },
                                enabled = isConfigured && busyProvider == null,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isActive) "Selected" else "Use This")
                            }
                        }

                        if (isConfigured) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { store.deleteApiKey(provider) }
                                        configured = configured + (provider to false)
                                        if (activeProvider == provider) {
                                            val next = AiProvider.values().firstOrNull {
                                                it != provider && configured[it] == true
                                            } ?: AiProvider.GEMINI
                                            store.setActiveProvider(next)
                                            activeProvider = next
                                        }
                                        messages = messages + (provider to "Saved key removed from this phone.")
                                    }
                                },
                                enabled = busyProvider == null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Remove saved key")
                            }
                        }

                        messages[provider]?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (it.contains("successful", true) || it.contains("Active")) {
                                    Color(0xFF00CFA5)
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }

            Text(
                text = "Fallback order: selected provider first, then other saved providers if the first one fails before producing a reply. API usage and charges depend on your provider account and selected model.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
