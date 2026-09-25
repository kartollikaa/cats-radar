package dev.catsradar.data.platform

import android.os.VibrationEffect
import android.os.Vibrator
import dev.catsradar.domain.platform.Haptics

private const val TICK_MILLIS = 20L

class VibratorHaptics(private val vibrator: Vibrator?) : Haptics {

    override fun tick() {
        vibrator?.vibrate(VibrationEffect.createOneShot(TICK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
