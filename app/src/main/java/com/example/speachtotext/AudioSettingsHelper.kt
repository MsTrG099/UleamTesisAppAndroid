package com.example.speachtotext

import android.content.Context
import android.util.Log

class AudioSettingsHelper(private val context: Context) {

    // CAMBIO: Usar el mismo SharedPreferences que SettingsActivity
    private val prefs = context.getSharedPreferences("AudioSettings", Context.MODE_PRIVATE)
    private val TAG = "AudioSettingsHelper"

    // === Métodos seguros para leer Int o String indistintamente ===
    private fun getSafeInt(key: String, defaultValue: Int): Int {
        val value = prefs.all[key]
        return when (value) {
            is Int -> value
            is String -> value.toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun getSafeBoolean(key: String, defaultValue: Boolean): Boolean {
        val value = prefs.all[key]
        return when (value) {
            is Boolean -> value
            is String -> value.equals("true", ignoreCase = true)
            else -> defaultValue
        }
    }

    private fun getSafeString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    private fun getSafeFloat(key: String, defaultValue: Float): Float {
        val value = prefs.all[key]
        return when (value) {
            is Float -> value
            is String -> value.toFloatOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    // === Preferencias de audio ===
    fun getSampleRate(): Int {
        return when (getSafeInt("sampleRate", 1)) {
            0 -> 8000
            1 -> 16000
            2 -> 22050
            3 -> 44100
            4 -> 48000
            else -> 16000
        }
    }

    fun getLanguageCode(): String {
        // Mapear el índice del spinner a códigos de idioma
        return when (getSafeInt("language", 0)) {
            0 -> "es-ES"  // Español (España)
            1 -> "es-MX"  // Español (México)
            2 -> "es-AR"  // Español (Argentina)
            3 -> "es-CO"  // Español (Colombia)
            else -> "es-ES"
        }
    }

    fun isPartialResultsEnabled(): Boolean {
        return getSafeBoolean("partialResults", true)
    }

    fun getMaxResults(): Int {
        return getSafeInt("maxResults", 3)
    }

    fun isVibrationEnabled(): Boolean {
        return getSafeBoolean("vibration", true)
    }

    fun isSoundFeedbackEnabled(): Boolean {
        return getSafeBoolean("soundFeedback", false)
    }

    // === Modo automático y preferencias de red ===
    fun isAutoModeSwitchEnabled(): Boolean {
        return getSafeBoolean("autoModeSwitch", true)
    }

    fun shouldPreferOnline(): Boolean {
        return getSafeBoolean("preferOnline", true)
    }

    // === Efectos de audio ===
    fun isAGCEnabled(): Boolean {
        return getSafeBoolean("agc", false)
    }

    fun isNoiseSuppressorEnabled(): Boolean {
        return getSafeBoolean("noiseSuppressor", false)
    }

    fun isEchoCancelerEnabled(): Boolean {
        return getSafeBoolean("echoCanceler", false)
    }

    // === Text-to-Speech (TTS) ===
    fun isTTSEnabled(): Boolean {
        return getSafeBoolean("enableTTS", true)
    }

    fun getTTSSpeed(): Float {
        // 0 = Lento (0.7), 1 = Normal (1.0), 2 = Rápido (1.3)
        return when (getSafeInt("ttsSpeed", 1)) {
            0 -> 0.7f
            1 -> 1.0f
            2 -> 1.3f
            else -> 1.0f
        }
    }

    fun getTTSPitch(): Float {
        // 0 = Grave (0.8), 1 = Normal (1.0), 2 = Agudo (1.2)
        return when (getSafeInt("ttsPitch", 1)) {
            0 -> 0.8f
            1 -> 1.0f
            2 -> 1.2f
            else -> 1.0f
        }
    }

    // === Tamaño de texto de transcripción ===
    fun getTranscriptionTextSize(): Float {
        // 0 = Pequeño (16sp), 1 = Normal (18sp), 2 = Grande (22sp)
        return when (getSafeInt("textSize", 1)) {
            0 -> 16f
            1 -> 18f
            2 -> 22f
            else -> 18f
        }
    }

    // === Depuración ===
    fun logCurrentSettings(tag: String = TAG) {
        Log.d(tag, buildString {
            appendLine("⚙️ Configuración actual de audio:")
            appendLine("• SampleRate: ${getSampleRate()} Hz")
            appendLine("• Idioma: ${getLanguageCode()}")
            appendLine("• Parciales: ${isPartialResultsEnabled()}")
            appendLine("• MaxResults: ${getMaxResults()}")
            appendLine("• Vibración: ${isVibrationEnabled()}")
            appendLine("• Sonido: ${isSoundFeedbackEnabled()}")
            appendLine("• AGC: ${isAGCEnabled()}")
            appendLine("• NoiseSuppressor: ${isNoiseSuppressorEnabled()}")
            appendLine("• EchoCanceler: ${isEchoCancelerEnabled()}")
            appendLine("• AutoSwitch: ${isAutoModeSwitchEnabled()}")
            appendLine("• PreferOnline: ${shouldPreferOnline()}")
            appendLine("• TTS Habilitado: ${isTTSEnabled()}")
            appendLine("• TTS Velocidad: ${getTTSSpeed()}")
            appendLine("• TTS Tono: ${getTTSPitch()}")
            appendLine("• Tamaño de texto: ${getTranscriptionTextSize()}sp")
        })
    }
}