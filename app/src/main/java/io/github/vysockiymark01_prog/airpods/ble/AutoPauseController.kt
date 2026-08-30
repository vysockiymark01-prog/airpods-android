package io.github.vysockiymark01_prog.airpods.ble

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Pauses whatever media is currently playing as soon as EITHER earbud is taken out of the ear —
 * matching real AirPods/iPhone behavior (Apple's own "Automatic Ear Detection" pauses on removing
 * either pod, not only when both are out) — and resumes it once every pod THIS controller paused
 * for is back in the ear.
 *
 * The resume is deliberately conditioned on "we're the one who paused it": [pausedForSides] only
 * ever gains an entry right before we send our own pause command, and only that entry being
 * cleared triggers a resume. This means we never touch playback the user paused themselves for an
 * unrelated reason — we only ever undo our own action. If the service restarts mid-removal this
 * state resets, so a resume can be missed across a process restart; that's an accepted trade-off
 * over risking an unwanted auto-resume.
 *
 * Implemented via synthetic media-key events, the same mechanism the system's own Bluetooth
 * headset handling uses — this works across apps without needing each media app's own API.
 */
class AutoPauseController(private val context: Context) {

    private var lastLeftInEar: Boolean? = null
    private var lastRightInEar: Boolean? = null
    private val pausedForSides = mutableSetOf<String>()

    fun onStatusUpdate(status: AirPodsStatus) {
        val previousLeftInEar = lastLeftInEar
        val previousRightInEar = lastRightInEar
        lastLeftInEar = status.leftInEar
        lastRightInEar = status.rightInEar

        var justPaused = false
        if (previousLeftInEar == true && !status.leftInEar) {
            pausedForSides += SIDE_LEFT
            justPaused = true
        }
        if (previousRightInEar == true && !status.rightInEar) {
            pausedForSides += SIDE_RIGHT
            justPaused = true
        }
        if (justPaused) {
            sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
            return
        }

        if (pausedForSides.isNotEmpty()) {
            if (previousLeftInEar == false && status.leftInEar) pausedForSides -= SIDE_LEFT
            if (previousRightInEar == false && status.rightInEar) pausedForSides -= SIDE_RIGHT
            if (pausedForSides.isEmpty()) {
                sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        }
    }

    private fun sendKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val eventTime = System.currentTimeMillis()
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    companion object {
        private const val SIDE_LEFT = "left"
        private const val SIDE_RIGHT = "right"
    }
}
