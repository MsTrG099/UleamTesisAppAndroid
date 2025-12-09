package com.example.speachtotext

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

object NotificationHelper {

    fun show(
        context: Context,
        message: String,
        duration: Int = Toast.LENGTH_SHORT
    ) {
        Toast.makeText(context, message, duration).show()

        val audioSettings = AudioSettingsHelper(context)
        if (audioSettings.isVibrationEnabled()) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (vibrator.hasVibrator()) {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            100,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }
}