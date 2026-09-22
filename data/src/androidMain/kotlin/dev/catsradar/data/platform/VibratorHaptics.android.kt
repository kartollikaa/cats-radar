package dev.catsradar.data.platform

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import dev.catsradar.domain.platform.Haptics

private const val TICK_MILLIS = 20L

class VibratorHaptics(context: Context) : Haptics {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)

    override fun tick() {
        vibrator?.vibrate(VibrationEffect.createOneShot(TICK_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
