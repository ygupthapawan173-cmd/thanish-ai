package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ai.PanduAiRepository
import com.example.control.PanduAccessibilityService
import com.example.omega.OmegaWakeService
import com.example.ui.JarvisStatus

private val ChDark = Color(0xFF061023)
private val ChPanel = Color(0xFF0B1B33)
private val ChCyan = Color(0xFF5AEEFF)
private val ChViolet = Color(0xFFB38AFF)
private val ChLight = Color(0xFFE8F9FF)
private val ChDim = Color(0xFF97B4D1)

@Composable
fun OmegaR104TopBar(
    status: JarvisStatus,
    speechLocaleLabel: String,
    ttsPresetLabel: String,
    autoSpeakEnabled: Boolean,
    hasMessages: Boolean,
    onToggleLocale: () -> Unit,
    onToggleAutoSpeak: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenHelp: () -> Unit,
    onRequestClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = when(status) {
        JarvisStatus.LISTENING -> "LISTENING"
        JarvisStatus.THINKING -> "THINKING"
        JarvisStatus.SPEAKING -> "SPEAKING"
        JarvisStatus.ALERT -> "ATTENTION"
        JarvisStatus.ONLINE -> "APP READY"
    }
    Surface(
        modifier=modifier.fillMaxWidth(),
        color=ChDark,
        shape=RoundedCornerShape(bottomStart=22.dp,bottomEnd=22.dp),
        border=BorderStroke(1.dp,Brush.horizontalGradient(listOf(ChCyan,ChViolet)))
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),
            verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).border(1.5.dp,ChCyan,CircleShape),
                    contentAlignment=Alignment.Center) {
                    Text("Ω",color=ChCyan,style=MaterialTheme.typography.headlineMedium)
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("OMEGA  /  JARVIS",color=ChLight,style=MaterialTheme.typography.titleMedium,
                        fontWeight=FontWeight.Bold,maxLines=1)
                    Text("R106 · NEURAL COMMAND",color=ChCyan,
                        style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
                IconButton(onClick=onOpenHelp,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Default.HelpOutline,contentDescription="Help",tint=ChCyan)
                }
                IconButton(onClick=onOpenTtsSettings,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Default.Tune,contentDescription="Voice settings",tint=ChViolet)
                }
                IconButton(onClick=onRequestClearChat,enabled=hasMessages,modifier=Modifier.size(48.dp)) {
                    Icon(Icons.Default.DeleteOutline,contentDescription="Clear conversation",
                        tint=if(hasMessages) ChDim else ChDim.copy(alpha=.35f))
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).background(
                    if(status==JarvisStatus.ALERT) Color(0xFFFFAC7C) else ChCyan, CircleShape))
                Text(state,color=ChCyan,style=MaterialTheme.typography.labelSmall,
                    modifier=Modifier.weight(1f))
                Surface(onClick=onToggleLocale,color=ChPanel,shape=RoundedCornerShape(10.dp)) {
                    Text(speechLocaleLabel,Modifier.padding(horizontal=9.dp,vertical=7.dp),
                        color=ChCyan,style=MaterialTheme.typography.labelSmall)
                }
                Surface(onClick=onToggleAutoSpeak,color=ChPanel,shape=RoundedCornerShape(10.dp)) {
                    Text(if(autoSpeakEnabled) "TTS ON" else "TTS OFF",
                        Modifier.padding(horizontal=9.dp,vertical=7.dp),
                        color=if(autoSpeakEnabled) ChViolet else ChDim,
                        style=MaterialTheme.typography.labelSmall)
                }
                Text(ttsPresetLabel,color=ChDim,style=MaterialTheme.typography.labelSmall,
                    maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun OmegaR104BottomNavBar(
    selectedTab: CyberpunkTab,
    onSelectTab: (CyberpunkTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier=modifier.fillMaxWidth(),
        shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp),
        color=ChDark,
        border=BorderStroke(1.dp,Brush.horizontalGradient(listOf(ChCyan,ChViolet)))
    ) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=4.dp,vertical=3.dp),
            horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
            CyberpunkTab.entries.forEach { tab ->
                val selected=tab==selectedTab
                val tint=if(selected) ChCyan else ChDim
                Column(
                    Modifier.weight(1f).heightIn(min=48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if(selected) ChCyan.copy(alpha=.13f) else Color.Transparent)
                        .then(if(selected) Modifier.border(1.dp,ChCyan.copy(alpha=.66f),
                            RoundedCornerShape(14.dp)) else Modifier)
                        .clickable { onSelectTab(tab) }
                        .testTag(tab.testTag)
                        .padding(vertical=3.dp),
                    horizontalAlignment=Alignment.CenterHorizontally,
                    verticalArrangement=Arrangement.spacedBy(4.dp)
                ) {
                    Icon(if(selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription=stringResource(tab.titleRes),tint=tint,
                        modifier=Modifier.size(if(selected) 23.dp else 20.dp))
                    Text(stringResource(tab.titleRes).uppercase(),color=tint,
                        fontSize=9.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun OmegaR104SettingsHeader(
    onOpenOmega: () -> Unit,
    onRefresh: () -> Unit
) {
    val context=LocalContext.current
    val wake by OmegaWakeService.active.collectAsState()
    val a11y by PanduAccessibilityService.isConnected.collectAsState()
    val accessibilityEvent by PanduAccessibilityService.connectionEvent.collectAsState()
    val lastRoute by PanduAiRepository.lastRoute.collectAsState()
    val requestState by PanduAiRepository.requestState.collectAsState()
    val wakeReady by OmegaWakeService.recognitionReady.collectAsState()
    val microphone=ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==
        PackageManager.PERMISSION_GRANTED
    Surface(
        color=ChPanel,shape=RoundedCornerShape(24.dp),
        border=BorderStroke(1.dp,Brush.horizontalGradient(listOf(ChCyan,ChViolet))),
        modifier=Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("OMEGA  /  CONTROL LAB",color=ChCyan,style=MaterialTheme.typography.labelSmall)
            Text("PERSONALIZE YOUR JARVIS",color=ChLight,
                style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("Real permissions, voice engine, accessibility and AI connection diagnostics",
                color=ChDim,style=MaterialTheme.typography.bodySmall)
            HorizontalDivider(color=ChCyan.copy(alpha=.2f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                R104StateCell("MIC",if(microphone) "ALLOWED" else "DENIED",Modifier.weight(1f))
                R104StateCell("WAKE",if(wakeReady) "LISTENING" else if(wake) "STARTING" else "STOPPED",Modifier.weight(1f))
                R104StateCell("A11Y",if(a11y) "ON" else "OFF",Modifier.weight(1f))
            }
            Text(accessibilityEvent,color=ChDim,style=MaterialTheme.typography.bodySmall)
            Text(requestState,color=ChCyan,style=MaterialTheme.typography.labelMedium)
            Text("LAST AI SUCCESS: " + lastRoute,color=ChDim,
                style=MaterialTheme.typography.bodySmall,maxLines=2)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(onClick=onOpenOmega,modifier=Modifier.weight(1f).heightIn(min=48.dp),
                    colors=ButtonDefaults.buttonColors(containerColor=ChCyan,contentColor=ChDark)) {
                    Icon(Icons.Default.GraphicEq,contentDescription=null,modifier=Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("VOICE & AI",maxLines=1)
                }
                OutlinedButton(onClick=onRefresh,modifier=Modifier.heightIn(min=48.dp)) {
                    Icon(Icons.Default.Refresh,contentDescription="Refresh permissions")
                }
            }
        }
    }
}

@Composable
private fun R104StateCell(title:String,value:String,modifier:Modifier=Modifier) {
    Column(modifier.background(ChDark, RoundedCornerShape(12.dp)).padding(9.dp)) {
        Text(title,color=ChDim,style=MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(4.dp))
        Text(value,color=ChCyan,style=MaterialTheme.typography.labelSmall,
            maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
