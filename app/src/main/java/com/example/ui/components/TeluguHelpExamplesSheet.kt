package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.ArcGlowBlue
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceElevated
import com.example.ui.theme.JarvisVoid
import com.example.ui.theme.MutedHudBlue
import com.example.ui.theme.NeonSapphire
import com.example.ui.theme.SlateCyan

private data class TeluguCommandExample(
    val teluguCommand: String,
    val meaningHint: String
)

private data class TeluguCommandCategory(
    val title: String,
    val icon: ImageVector,
    val examples: List<TeluguCommandExample>
)

private val TELUGU_COMMAND_CATEGORIES = listOf(
    TeluguCommandCategory(
        title = "సరదా & కామెడీ (FUN & WIT)",
        icon = Icons.Default.EmojiEmotions,
        examples = listOf(
            TeluguCommandExample(
                teluguCommand = "పాండు బాబు, ఒక సూపర్ తెలుగు జోక్ చెప్పు!",
                meaningHint = "Tell me a hilarious Telugu joke"
            ),
            TeluguCommandExample(
                teluguCommand = "మంచి మాస్ పంచ్ డైలాగ్ కొట్టు బాస్!",
                meaningHint = "Drop a witty mass punchline"
            ),
            TeluguCommandExample(
                teluguCommand = "తెలుగులో ఒక తెలివైన పొడుపు కథ అడుగు",
                meaningHint = "Ask me a clever Telugu riddle"
            )
        )
    ),
    TeluguCommandCategory(
        title = "రోజువారీ సహాయం & టెక్ (DAILY HELP & TECH)",
        icon = Icons.Default.AutoAwesome,
        examples = listOf(
            TeluguCommandExample(
                teluguCommand = "AI గురించి తెలుగులో సింపుల్‌గా వివరించు",
                meaningHint = "Explain AI simply in Telugu"
            ),
            TeluguCommandExample(
                teluguCommand = "ఈ రోజు కిక్ ఇచ్చే మోటివేషన్ మాట చెప్పు",
                meaningHint = "Give me energizing motivation today"
            ),
            TeluguCommandExample(
                teluguCommand = "త్వరగా చదువుకోవడానికి మంచి స్టడీ టిప్స్ చెప్పు",
                meaningHint = "Share smart study tips"
            )
        )
    ),
    TeluguCommandCategory(
        title = "ఆహారం & లైఫ్‌స్టైల్ (FOOD & LIFESTYLE)",
        icon = Icons.Default.Restaurant,
        examples = listOf(
            TeluguCommandExample(
                teluguCommand = "ఈ రాత్రి డిన్నర్ కోసం స్పెషల్ తెలుగు వంటకం చెప్పు",
                meaningHint = "Suggest a special Telugu dinner dish"
            ),
            TeluguCommandExample(
                teluguCommand = "త్వరగా తయారయ్యే హెల్తీ టిఫిన్ ఐడియా ఇవ్వు",
                meaningHint = "Quick healthy breakfast idea"
            )
        )
    ),
    TeluguCommandCategory(
        title = "ఫోన్ కంట్రోల్ (PHONE & DEVICE CONTROL)",
        icon = Icons.Default.PhoneAndroid,
        examples = listOf(
            TeluguCommandExample(
                teluguCommand = "Torch on chey",
                meaningHint = "Turn device flashlight ON"
            ),
            TeluguCommandExample(
                teluguCommand = "Volume penchu",
                meaningHint = "Increase media volume"
            ),
            TeluguCommandExample(
                teluguCommand = "బ్యాటరీ ఎంత ఉంది",
                meaningHint = "Check battery level & temperature"
            ),
            TeluguCommandExample(
                teluguCommand = "Wi-Fi settings open chey",
                meaningHint = "Open Wi-Fi system panel"
            )
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeluguHelpExamplesSheet(
    onRunExample: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisSurface,
        contentColor = IceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("help_examples_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.help_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = IceWhite
                    )
                    Text(
                        text = stringResource(R.string.help_sheet_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateCyan
                    )
                }
            }

            // Voice & HUD Pro Tips Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = JarvisSurfaceElevated,
                border = BorderStroke(1.dp, NeonSapphire.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.help_voice_tip_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                    Text(
                        text = stringResource(R.string.help_voice_tip_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IceWhite.copy(alpha = 0.92f)
                    )
                }
            }

            // Categorized Telugu Examples
            var runningIndex = 0
            TELUGU_COMMAND_CATEGORIES.forEach { category ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = ArcGlowBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }

                    category.examples.forEach { example ->
                        val itemIndex = runningIndex++
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) {
                                    onRunExample(example.teluguCommand)
                                }
                                .testTag("help_example_item_$itemIndex"),
                            shape = RoundedCornerShape(14.dp),
                            color = JarvisVoid.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.32f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "“${example.teluguCommand}”",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = IceWhite
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${example.meaningHint} • ${stringResource(R.string.help_tap_to_try)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedHudBlue
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.help_tap_to_try),
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
