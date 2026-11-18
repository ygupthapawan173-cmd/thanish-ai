package com.example.control

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.view.KeyEvent

/**
 * Opens YouTube search results for a requested artist/hero or specific song.
 * Media transport keys are sent to the active media session; availability depends on
 * the music app and Android's current media-session state.
 */
class MusicControlManager(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun search(query: String, isArtistCollection: Boolean): CommandExecutionResult {
        val searchText = if (isArtistCollection) "$query songs" else query
        val url = Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(searchText))
        val youtubeIntent = Intent(Intent.ACTION_VIEW, url).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val browserIntent = Intent(Intent.ACTION_VIEW, url).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            val intent = if (youtubeIntent.resolveActivity(appContext.packageManager) != null) {
                youtubeIntent
            } else {
                browserIntent
            }
            if (intent.resolveActivity(appContext.packageManager) == null) {
                CommandExecutionResult(false, "బాస్, YouTube లేదా బ్రౌజర్ అందుబాటులో లేదు.")
            } else {
                appContext.startActivity(intent)
                val response = if (isArtistCollection) {
                    "$query పాటల కోసం YouTube search తెరిచాను. వచ్చిన ఫలితాల్లో పాటను ఎంచుకోండి."
                } else {
                    "$query పాట కోసం YouTube search తెరిచాను. సరైన పాటను ఎంచుకోండి."
                }
                CommandExecutionResult(true, response)
            }
        } catch (error: Exception) {
            CommandExecutionResult(false, "బాస్, YouTube search తెరవలేకపోయాను: ${error.localizedMessage ?: "తెలియని సమస్య"}")
        }
    }

    fun control(action: MusicTransportAction): CommandExecutionResult {
        val keyCode = when (action) {
            MusicTransportAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MusicTransportAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            MusicTransportAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MusicTransportAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MusicTransportAction.STOP -> KeyEvent.KEYCODE_MEDIA_STOP
        }
        return try {
            val now = android.os.SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            CommandExecutionResult(true, when (action) {
                MusicTransportAction.NEXT -> "తర్వాతి పాటకు వెళ్లమని పంపించాను."
                MusicTransportAction.PREVIOUS -> "ముందు పాటకు వెళ్లమని పంపించాను."
                MusicTransportAction.PAUSE -> "పాట pause చేయమని పంపించాను."
                MusicTransportAction.PLAY -> "పాట resume చేయమని పంపించాను."
                MusicTransportAction.STOP -> "పాట stop చేయమని పంపించాను."
            })
        } catch (error: Exception) {
            CommandExecutionResult(false, "బాస్, media control పనిచేయలేదు: ${error.localizedMessage ?: "తెలియని సమస్య"}")
        }
    }
}
