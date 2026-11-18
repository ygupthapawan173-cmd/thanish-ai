package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatMessageEntity
import com.example.ui.theme.ArcGlowBlue
import com.example.ui.theme.CyberOrange
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HudOnlineGreen
import com.example.ui.theme.IceWhite
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisUserBubble
import com.example.ui.theme.MutedHudBlue
import com.example.ui.theme.NeonSapphire
import com.example.ui.theme.SlateCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    isCurrentlySpeaking: Boolean,
    onSpeakClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == ChatMessageEntity.ROLE_USER
    val formattedTime = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("chat_message_${message.id}"),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Surface(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(34.dp),
                shape = CircleShape,
                border = BorderStroke(
                    width = 1.5.dp,
                    color = if (isCurrentlySpeaking) HudOnlineGreen else ElectricCyan.copy(alpha = 0.7f)
                )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.pandu_jarvis_icon_1794946931645),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = if (isUser) JarvisUserBubble else JarvisSurface.copy(alpha = 0.94f),
            border = BorderStroke(
                width = if (isCurrentlySpeaking) 1.6.dp else 1.1.dp,
                brush = when {
                    isCurrentlySpeaking -> androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(HudOnlineGreen, ElectricCyan)
                    )
                    isUser -> androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(CyberOrange.copy(alpha = 0.85f), NeonSapphire.copy(alpha = 0.85f))
                    )
                    else -> cyberDualNeonBrush()
                }
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 14.dp,
                    end = 10.dp,
                    top = 10.dp,
                    bottom = 8.dp
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isUser) {
                            stringResource(R.string.you_label)
                        } else {
                            stringResource(R.string.pandu_label)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) ArcGlowBlue else ElectricCyan
                    )
                    if (isUser && message.isVoiceInput) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = stringResource(R.string.voice_badge),
                            tint = ElectricCyan,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IceWhite
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedHudBlue
                    )

                    if (!isUser) {
                        IconButton(
                            onClick = onSpeakClick,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("speak_message_${message.id}")
                        ) {
                            Icon(
                                imageVector = if (isCurrentlySpeaking) {
                                    Icons.Default.Stop
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = if (isCurrentlySpeaking) {
                                    stringResource(R.string.stop_voice_cd)
                                } else {
                                    stringResource(R.string.replay_voice_cd)
                                },
                                tint = if (isCurrentlySpeaking) HudOnlineGreen else ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingAssistantBubble(
    partialText: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "streaming_cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(34.dp),
            shape = CircleShape,
            border = BorderStroke(1.5.dp, ArcGlowBlue)
        ) {
            Image(
                painter = painterResource(id = R.drawable.pandu_jarvis_icon_1794946931645),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            modifier = Modifier.widthIn(max = 460.dp),
            shape = RoundedCornerShape(
                topStart = 4.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp
            ),
            color = JarvisSurface.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = stringResource(R.string.pandu_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (partialText.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.status_thinking),
                        style = MaterialTheme.typography.bodyLarge,
                        color = SlateCyan.copy(alpha = cursorAlpha)
                    )
                } else {
                    Text(
                        text = "$partialText ▌",
                        style = MaterialTheme.typography.bodyLarge,
                        color = IceWhite
                    )
                }
            }
        }
    }
}
