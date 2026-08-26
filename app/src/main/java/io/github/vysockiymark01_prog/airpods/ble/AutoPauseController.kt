package io.github.vysockiymark01_prog.airpods.ble

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Pauses whatever media is currently playing when both earbuds (or the only currently-worn one)
 * are taken out of the ear. Implemented via a synthetic media-key event, the same mechanism the
 * system's own Bluetooth headset handling uses — this works across apps without needing each
 * media app's own API.
 *
 * We deliberately do NOT auto-resume on re-insertion: Apple's own behavior is asymmetric too
 * (resume-on-wear is a separate, less reliable heuristic), and the spec only asked for
 * pause-on-removal.
 */
class AutoPauseController(private val context: Context) {

    private var lastBothOut: Boolean? = null

    fun onStatusUpdate(status: AirPodsStatus) {
        val bothOut = !status.leftInEar && !status.rightInEar
        val previous = lastBothOut
        lastBothOut = bothOut
        if (bothOut && previous == false) {
            sendPause()
        }
    }

    private fun sendPause() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val eventTime = System.currentTimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }
}
