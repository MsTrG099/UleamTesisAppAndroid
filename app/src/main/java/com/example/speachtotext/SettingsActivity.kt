package com.example.speachtotext

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    // Views - Configuración de Audio
    private lateinit var btnBack: ImageButton
    private lateinit var spinnerSampleRate: Spinner
    private lateinit var spinnerAudioSource: Spinner
    private lateinit var spinnerChannelConfig: Spinner
    private lateinit var spinnerAudioEncoding: Spinner
    private lateinit var spinnerBufferSize: Spinner

    // Switches - Efectos de Audio
    private lateinit var switchAGC: Switch
    private lateinit var switchNoiseSuppressor: Switch
    private lateinit var switchEchoCanceler: Switch

    // Configuración de Reconocimiento
    private lateinit var spinnerLanguage: Spinner
    private lateinit var switchPartialResults: Switch
    private lateinit var switchContinuousRecognition: Switch
    private lateinit var spinnerMaxResults: Spinner

    // Configuración de Modo
    private lateinit var switchAutoModeSwitch: Switch
    private lateinit var switchPreferOnline: Switch

    // Configuración de UI
    private lateinit var switchVibration: Switch
    private lateinit var switchSoundFeedback: Switch

    // Botones
    private lateinit var btnSave: Button
    private lateinit var btnRestoreDefaults: Button
    private lateinit var tvInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupSpinners()
        loadSettings()
        setupListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)

        // Audio básico
        spinnerSampleRate = findViewById(R.id.spinnerSampleRate)
        spinnerAudioSource = findViewById(R.id.spinnerAudioSource)
        spinnerChannelConfig = findViewById(R.id.spinnerChannelConfig)
        spinnerAudioEncoding = findViewById(R.id.spinnerAudioEncoding)
        spinnerBufferSize = findViewById(R.id.spinnerBufferSize)

        // Efectos de audio
        switchAGC = findViewById(R.id.switchAGC)
        switchNoiseSuppressor = findViewById(R.id.switchNoiseSuppressor)
        switchEchoCanceler = findViewById(R.id.switchEchoCanceler)

        // Reconocimiento
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        switchPartialResults = findViewById(R.id.switchPartialResults)
        switchContinuousRecognition = findViewById(R.id.switchContinuousRecognition)
        spinnerMaxResults = findViewById(R.id.spinnerMaxResults)

        // Modo
        switchAutoModeSwitch = findViewById(R.id.switchAutoModeSwitch)
        switchPreferOnline = findViewById(R.id.switchPreferOnline)

        // UI
        switchVibration = findViewById(R.id.switchVibration)
        switchSoundFeedback = findViewById(R.id.switchSoundFeedback)

        // Botones
        btnSave = findViewById(R.id.btnSave)
        btnRestoreDefaults = findViewById(R.id.btnRestoreDefaults)
        tvInfo = findViewById(R.id.tvInfo)
    }

    private fun setupSpinners() {
        // Sample Rate
        val sampleRates = arrayOf(
            "8000 Hz - Calidad Teléfono",
            "16000 Hz - Reconocimiento Voz (Recomendado)",
            "22050 Hz - Calidad Media",
            "44100 Hz - Calidad CD",
            "48000 Hz - Calidad Profesional"
        )
        spinnerSampleRate.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sampleRates)

        // Audio Source
        val audioSources = arrayOf(
            "DEFAULT - Por defecto",
            "MIC - Micrófono estándar",
            "VOICE_RECOGNITION - Optimizado para voz (Recomendado)",
            "VOICE_COMMUNICATION - Llamadas (cancela eco)",
            "CAMCORDER - Para video (estéreo)",
            "UNPROCESSED - Sin procesamiento (raw)"
        )
        spinnerAudioSource.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, audioSources)

        // Channel Configuration
        val channels = arrayOf(
            "MONO - 1 canal (Recomendado para voz)",
            "STEREO - 2 canales (Música)"
        )
        spinnerChannelConfig.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, channels)

        // Audio Encoding
        val encodings = arrayOf(
            "PCM_8BIT - 8 bits (Baja calidad)",
            "PCM_16BIT - 16 bits (Estándar)",
            "PCM_FLOAT - 32 bits float (Alta precisión)"
        )
        spinnerAudioEncoding.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, encodings)

        // Buffer Size
        val bufferSizes = arrayOf(
            "Mínimo x1 - Menor latencia",
            "Mínimo x2 - Recomendado",
            "Mínimo x3 - Más estable",
            "Mínimo x4 - Máxima estabilidad"
        )
        spinnerBufferSize.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, bufferSizes)

        // Language
        val languages = arrayOf(
            "es-ES - Español (España)",
            "es-MX - Español (México)",
            "es-AR - Español (Argentina)",
            "es-CO - Español (Colombia)"
        )
        spinnerLanguage.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, languages)

        // Max Results
        val maxResults = arrayOf("1 resultado", "3 resultados", "5 resultados", "10 resultados")
        spinnerMaxResults.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, maxResults)
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("AudioSettings", MODE_PRIVATE)

        // Cargar configuración guardada o valores por defecto
        spinnerSampleRate.setSelection(prefs.getInt("sampleRate", 1)) // 16000 Hz
        spinnerAudioSource.setSelection(prefs.getInt("audioSource", 2)) // VOICE_RECOGNITION
        spinnerChannelConfig.setSelection(prefs.getInt("channelConfig", 0)) // MONO
        spinnerAudioEncoding.setSelection(prefs.getInt("audioEncoding", 1)) // PCM_16BIT
        spinnerBufferSize.setSelection(prefs.getInt("bufferSize", 1)) // x2

        switchAGC.isChecked = prefs.getBoolean("agc", true)
        switchNoiseSuppressor.isChecked = prefs.getBoolean("noiseSuppressor", true)
        switchEchoCanceler.isChecked = prefs.getBoolean("echoCanceler", false)

        spinnerLanguage.setSelection(prefs.getInt("language", 0)) // es-ES
        switchPartialResults.isChecked = prefs.getBoolean("partialResults", true)
        switchContinuousRecognition.isChecked = prefs.getBoolean("continuousRecognition", false)
        spinnerMaxResults.setSelection(prefs.getInt("maxResults", 0)) // 1

        switchAutoModeSwitch.isChecked = prefs.getBoolean("autoModeSwitch", true)
        switchPreferOnline.isChecked = prefs.getBoolean("preferOnline", true)

        switchVibration.isChecked = prefs.getBoolean("vibration", true)
        switchSoundFeedback.isChecked = prefs.getBoolean("soundFeedback", false)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveSettings()
        }

        btnRestoreDefaults.setOnClickListener {
            restoreDefaults()
        }

        // Mostrar info según la selección
        spinnerSampleRate.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateInfoText()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("AudioSettings", MODE_PRIVATE)
        val editor = prefs.edit()

        // Guardar todas las configuraciones
        editor.putInt("sampleRate", spinnerSampleRate.selectedItemPosition)
        editor.putInt("audioSource", spinnerAudioSource.selectedItemPosition)
        editor.putInt("channelConfig", spinnerChannelConfig.selectedItemPosition)
        editor.putInt("audioEncoding", spinnerAudioEncoding.selectedItemPosition)
        editor.putInt("bufferSize", spinnerBufferSize.selectedItemPosition)

        editor.putBoolean("agc", switchAGC.isChecked)
        editor.putBoolean("noiseSuppressor", switchNoiseSuppressor.isChecked)
        editor.putBoolean("echoCanceler", switchEchoCanceler.isChecked)

        editor.putInt("language", spinnerLanguage.selectedItemPosition)
        editor.putBoolean("partialResults", switchPartialResults.isChecked)
        editor.putBoolean("continuousRecognition", switchContinuousRecognition.isChecked)
        editor.putInt("maxResults", spinnerMaxResults.selectedItemPosition)

        editor.putBoolean("autoModeSwitch", switchAutoModeSwitch.isChecked)
        editor.putBoolean("preferOnline", switchPreferOnline.isChecked)

        editor.putBoolean("vibration", switchVibration.isChecked)
        editor.putBoolean("soundFeedback", switchSoundFeedback.isChecked)

        editor.apply()

        Toast.makeText(this, "✓ Configuración guardada", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun restoreDefaults() {
        spinnerSampleRate.setSelection(1) // 16000 Hz
        spinnerAudioSource.setSelection(2) // VOICE_RECOGNITION
        spinnerChannelConfig.setSelection(0) // MONO
        spinnerAudioEncoding.setSelection(1) // PCM_16BIT
        spinnerBufferSize.setSelection(1) // x2

        switchAGC.isChecked = true
        switchNoiseSuppressor.isChecked = true
        switchEchoCanceler.isChecked = false

        spinnerLanguage.setSelection(0) // es-ES
        switchPartialResults.isChecked = true
        switchContinuousRecognition.isChecked = false
        spinnerMaxResults.setSelection(0) // 1

        switchAutoModeSwitch.isChecked = true
        switchPreferOnline.isChecked = true

        switchVibration.isChecked = true
        switchSoundFeedback.isChecked = false

        Toast.makeText(this, "↺ Valores por defecto restaurados", Toast.LENGTH_SHORT).show()
    }

    private fun updateInfoText() {
        val sampleRate = spinnerSampleRate.selectedItemPosition
        val info = when (sampleRate) {
            0 -> "Calidad básica, menor consumo de batería"
            1 -> "Óptimo para reconocimiento de voz"
            2 -> "Balance entre calidad y rendimiento"
            3 -> "Alta calidad, mayor consumo"
            4 -> "Máxima calidad, alto consumo de batería"
            else -> ""
        }
        tvInfo.text = "ℹ️ $info"
    }
}