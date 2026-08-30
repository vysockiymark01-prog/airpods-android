package io.github.vysockiymark01_prog.airpods.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect

/**
 * Listens for the two standard system broadcasts audio apps send when they start/stop playback,
 * and hands the session id to [PerSessionEqualizerController] — see that class's doc for why this
 * exists (it's the fallback for devices that block global/session-0 audio effects). Deliberately
 * does no I/O here: [PerSessionEqualizerController] already keeps the last-known equalizer state
 * cached in memory (kept fresh by the foreground service), so attaching/detaching is a fast,
 * fully synchronous call — safe to do directly in `onReceive` without `goAsync()`.
 */
class AudioSessionEffectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR)
        if (sessionId == AudioEffect.ERROR) return
        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> PerSessionEqualizerController.attach(sessionId)
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> PerSessionEqualizerController.detach(sessionId)
        }
    }
}
