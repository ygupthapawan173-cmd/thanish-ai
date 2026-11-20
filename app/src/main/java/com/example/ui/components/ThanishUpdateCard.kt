package com.example.ui.components

import android.content.Intent
import.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val THANISH_RELEASES_API =
    "https://api.github.com/repos/ygupthapawan173-cmd/thanish-ai/releases/latest"

@Composable
fun ThanishUpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Installed version: ${BuildConfig.VERSION_NAME}") }
    var downloadUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("THANISH AI • App Updates", style = MaterialTheme.typography.titleMedium)
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Button(
                enabled = !checking,
                onClick = {
                    checking = true
                    status = "Checking GitHub Releases..."
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val connection = (URL(THANISH_RELEASES_API).openConnection() as HttpURLConnection)
                                connection.requestMethod = "GET"
                                connection.connectTimeout = 12000
                                connection.readTimeout = 12000
                                connection.setRequestProperty("Accept", "application/vnd.github+json")
                                try {
                                    if (connection.responseCode !in 200..299) {
                                        error("GitHub returned HTTP ${connection.responseCode}")
                                    }
                                    val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                                    val tag = json.optString("tag_name", "Latest")
                                    val page = json.optString("html_url")
                                    val assets = json.optJSONArray("assets")
                                    var apk: String? = null
                                    if (assets != null) {
                                        for (i in 0 until assets.length()) {
                                            val asset = assets.getJSONObject(i)
                                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                                apk = asset.optString("browser_download_url")
                                                break
                                            }
                                        }
                                    }
                                    Triple(tag, apk, page)
                                } finally {
                                    connection.disconnect()
                                }
                            }
                        }
                        checking = false
                        result.onSuccess { (tag, apk, page) ->
                            downloadUrl = apk ?: page
                            status = if (apk != null) {
                                "Latest release: $tag • APK available. Tap Download & Install."
                            } else {
                                "Latest release: $tag • No APK asset found. Open release page."
                            }
                        }.onFailure {
                            downloadUrl = null
                            status = "Update check failed: ${it.message ?: "Network error"}"
                        }
                    }
                }
            ) {
                Text(if (checking) "Checking..." else "Check for Updates")
            }
            OutlinedButton(
                enabled = downloadUrl != null,
                onClick = {
                    downloadUrl?.let { link ->
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Download / Open Release")
            }
            Text(
                "Updates are installed only after you approve Android's installer. Install updates signed with the same app key.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
