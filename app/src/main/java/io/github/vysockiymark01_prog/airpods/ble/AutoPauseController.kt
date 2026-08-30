package io.github.vysockiymark01_prog.airpods.ble

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Pauses whatever media is currently playing as soon as EITHER earbud is taken out of the ear —
 * matching real AirPods/iPhone behavior (Apple's own "Automatic Ear Detection" pauses on removing
 * either pod, not only when both are out). Implemented via a synthetic media-key event, the same
 * mechanism the system's own Bluetooth headset handling uses — this works across apps without
 * needing each media app's own API.
 *
 * We deliberately do NOT auto-resume on re-insertion: Apple's own behavior is asymmetric too
 * (resume-on-wear is a separate, less reliable heuristic), and the spec only asked for
 * pause-on-removal.
 */
class AutoPauseController(private val context: Context) {

    private var lastLeftInEar: Boolean? = null
    private var lastRightInEar: Boolean? = null

    fun onStatusUpdate(status: AirPodsStatus) {
        val previousLeftInEar = lastLeftInEar
        val previousRightInEar = lastRightInEar
        lastLeftInEar = status.leftInEar
        lastRightInEar = status.rightInEar

        // "Removed" = was in the ear on the previous reading and isn't anymore. Checking each pod
        // independently (rather than requiring both out) is what makes this fire the moment you
        // take out just one earbud, same as on iPhone.
        val leftJustRemoved = previousLeftInEar == true && !status.leftInEar
        val rightJustRemoved = previousRightInEar == true && !status.rightInEar
        if (leftJustRemoved || rightJustRemoved) {
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
