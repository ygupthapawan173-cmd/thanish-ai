package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ai.OpenRouterKeyStore
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.SlateCyan

@Composable
fun OpenRouterSettingsCard() {
    val context = LocalContext.current
    val store = remember { OpenRouterKeyStore(context) }
    var apiKey by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(store.getKey() != null) }
    var message by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = JarvisSurface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("OPENROUTER FREE AI", color = ElectricCyan)
            Text(
                if (configured) "API key saved securely. New chats use OpenRouter." else "Add your OpenRouter API key to enable cloud AI.",
                color = SlateCyan
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("OpenRouter API key") },
                placeholder = { Text("sk-or-v1-...") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (apiKey.trim().startsWith("sk-or-") && apiKey.trim().length > 20) {
                            store.saveKey(apiKey)
                            apiKey = ""
                            configured = true
                            message = "Key saved. Open a new chat to use OpenRouter."
                        } else {
                            message = "Valid OpenRouter key enter చేయండి."
                        }
                    },
                    enabled = apiKey.isNotBlank()
                ) { Text("Save key") }
                TextButton(onClick = {
                    store.clearKey()
                    apiKey = ""
                    configured = false
                    message = "OpenRouter key removed. Gemini fallback active."
                }, enabled = configured) { Text("Remove key") }
            }
            if (message.isNotBlank()) Text(message, color = IceWhite)
            Text("Key is stored using Android Keystore encryption. Never share it.", color = SlateCyan)
        }
    }
}
