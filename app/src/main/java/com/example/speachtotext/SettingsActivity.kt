package com.example.speachtotext

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class SettingsActivity : AppCompatActivity() {

    // Views
    private lateinit var btnBack: ImageButton
    private lateinit var cardSilent: CardView
    private lateinit var cardModerate: CardView
    private lateinit var cardNoisy: CardView
    private lateinit var cardOutdoor: CardView
    private lateinit var checkSilent: ImageView
    private lateinit var checkModerate: ImageView
    private lateinit var checkNoisy: ImageView
    private lateinit var checkOutdoor: ImageView
    private lateinit var spinnerLanguage: Spinner
    private lateinit var switchVibration: Switch
    private lateinit var switchSoundFeedback: Switch
    private lateinit var tvSelectedProfile: TextView

    // Views para TTS
    private lateinit var switchTTS: Switch
    private lateinit var spinnerTTSSpeed: Spinner
    private lateinit var spinnerTTSPitch: Spinner

    // Views para tema
    private lateinit var spinnerTheme: Spinner

    // Navegación inferior
    private var btnBottomHome: View? = null
    private var btnBottomHistory: View? = null

    // Perfil seleccionado
    private var selectedProfile = "moderate"

    // Perfiles de configuración
    data class AudioProfile(
        val sampleRate: Int,
        val audioSource: Int,
        val channelConfig: Int,
        val audioEncoding: Int,
        val bufferSize: Int,
        val agc: Boolean,
        val noiseSuppressor: Boolean,
        val echoCanceler: Boolean,
        val partialResults: Boolean,
        val continuousRecognition: Boolean,
        val maxResults: Int,
        val autoModeSwitch: Boolean,
        val preferOnline: Boolean
    )

    private val profiles = mapOf(
        "silent" to AudioProfile(
            sampleRate = 3, audioSource = 1, channelConfig = 0,
            audioEncoding = 1, bufferSize = 1, agc = false,
            noiseSuppressor = false, echoCanceler = false,
            partialResults = true, continuousRecognition = false,
            maxResults = 1, autoModeSwitch = true, preferOnline = true
        ),
        "moderate" to AudioProfile(
            sampleRate = 1, audioSource = 2, channelConfig = 0,
            audioEncoding = 1, bufferSize = 1, agc = true,
            noiseSuppressor = true, echoCanceler = false,
            partialResults = true, continuousRecognition = false,
            maxResults = 1, autoModeSwitch = true, preferOnline = true
        ),
        "noisy" to AudioProfile(
            sampleRate = 1, audioSource = 2, channelConfig = 0,
            audioEncoding = 1, bufferSize = 2, agc = true,
            noiseSuppressor = true, echoCanceler = true,
            partialResults = true, continuousRecognition = false,
            maxResults = 1, autoModeSwitch = true, preferOnline = true
        ),
        "outdoor" to AudioProfile(
            sampleRate = 1, audioSource = 3, channelConfig = 0,
            audioEncoding = 1, bufferSize = 2, agc = true,
            noiseSuppressor = true, echoCanceler = true,
            partialResults = true, continuousRecognition = false,
            maxResults = 1, autoModeSwitch = true, preferOnline = true
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)  // ✅ Aplicar tema ANTES de setContentView
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        initViews()
        setupLanguageSpinner()
        setupTTSSpinners()
        setupThemeSpinner()
        loadSettings()
        setupListeners()
        setupBackPressHandler()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        cardSilent = findViewById(R.id.cardSilent)
        cardModerate = findViewById(R.id.cardModerate)
        cardNoisy = findViewById(R.id.cardNoisy)
        cardOutdoor = findViewById(R.id.cardOutdoor)
        checkSilent = findViewById(R.id.checkSilent)
        checkModerate = findViewById(R.id.checkModerate)
        checkNoisy = findViewById(R.id.checkNoisy)
        checkOutdoor = findViewById(R.id.checkOutdoor)
        spinnerLanguage = findViewById(R.id.spinnerLanguage)
        switchVibration = findViewById(R.id.switchVibration)
        switchSoundFeedback = findViewById(R.id.switchSoundFeedback)
        tvSelectedProfile = findViewById(R.id.tvSelectedProfile)

        // Views de TTS
        switchTTS = findViewById(R.id.switchTTS)
        spinnerTTSSpeed = findViewById(R.id.spinnerTTSSpeed)
        spinnerTTSPitch = findViewById(R.id.spinnerTTSPitch)

        // View de tema
        spinnerTheme = findViewById(R.id.spinnerTheme)

        btnBottomHome = findViewById(R.id.btnBottomHome)
        btnBottomHistory = findViewById(R.id.btnBottomHistory)
    }

    private fun setupLanguageSpinner() {
        val languages = arrayOf(
            "🇪🇸 Español (España)",
            "🇲🇽 Español (México)",
            "🇦🇷 Español (Argentina)",
            "🇨🇴 Español (Colombia)"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerLanguage.adapter = adapter
    }

    private fun setupTTSSpinners() {
        val speeds = arrayOf("🐢 Lento", "🚶 Normal", "🏃 Rápido")
        val speedAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, speeds)
        speedAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTTSSpeed.adapter = speedAdapter

        val pitches = arrayOf("🔉 Grave", "🔊 Normal", "🔔 Agudo")
        val pitchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, pitches)
        pitchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTTSPitch.adapter = pitchAdapter
    }

    private fun setupThemeSpinner() {
        val themes = arrayOf(
            "💡 Modo Claro",
            "🌙 Modo Oscuro",
            "🔄 Seguir sistema"
        )
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, themes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTheme.adapter = adapter
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("AudioSettings", MODE_PRIVATE)

        selectedProfile = prefs.getString("selectedProfile", "moderate") ?: "moderate"

        val languageIndex = prefs.getInt("language", 0)
        spinnerLanguage.setSelection(languageIndex)

        switchVibration.isChecked = prefs.getBoolean("vibration", true)
        switchSoundFeedback.isChecked = prefs.getBoolean("soundFeedback", false)

        // Cargar configuración de TTS
        switchTTS.isChecked = prefs.getBoolean("enableTTS", true)
        spinnerTTSSpeed.setSelection(prefs.getInt("ttsSpeed", 1))
        spinnerTTSPitch.setSelection(prefs.getInt("ttsPitch", 1))

        // ✅ ACTUALIZADO: Usar ThemeHelper para cargar el tema
        spinnerTheme.setSelection(ThemeHelper.getCurrentThemeIndex(this))

        updateProfileSelection(selectedProfile)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        cardSilent.setOnClickListener { selectProfile("silent") }
        cardModerate.setOnClickListener { selectProfile("moderate") }
        cardNoisy.setOnClickListener { selectProfile("noisy") }
        cardOutdoor.setOnClickListener { selectProfile("outdoor") }

        btnBottomHome?.setOnClickListener { saveAndFinish() }
        btnBottomHistory?.setOnClickListener {
            saveAndFinish()
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        spinnerLanguage.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                saveSettings()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        switchVibration.setOnCheckedChangeListener { _, _ -> saveSettings() }
        switchSoundFeedback.setOnCheckedChangeListener { _, _ -> saveSettings() }

        // Listeners de TTS
        switchTTS.setOnCheckedChangeListener { _, isChecked ->
            spinnerTTSSpeed.isEnabled = isChecked
            spinnerTTSPitch.isEnabled = isChecked
            saveSettings()
        }

        spinnerTTSSpeed.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                saveSettings()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        spinnerTTSPitch.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                saveSettings()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // ✅ ACTUALIZADO: Listener de tema simplificado
        spinnerTheme.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                applyTheme(position)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }

    // ✅ ACTUALIZADO: Usar ThemeHelper para aplicar el tema
    private fun applyTheme(themeIndex: Int) {
        // Usar ThemeHelper para guardar y aplicar
        ThemeHelper.saveAndApplyTheme(this, themeIndex)

        val themeName = when(themeIndex) {
            0 -> "Modo Claro"
            1 -> "Modo Oscuro"
            2 -> "Seguir sistema"
            else -> "Desconocido"
        }

        Toast.makeText(this, "✓ Tema aplicado: $themeName", Toast.LENGTH_SHORT).show()
    }

    private fun selectProfile(profile: String) {
        selectedProfile = profile
        updateProfileSelection(profile)
        saveSettings()

        val profileName = when(profile) {
            "silent" -> "Entorno Silencioso"
            "moderate" -> "Entorno Moderado"
            "noisy" -> "Entorno Ruidoso"
            "outdoor" -> "Exterior/Viento"
            else -> "Desconocido"
        }

        Toast.makeText(this, "✓ Perfil aplicado: $profileName", Toast.LENGTH_SHORT).show()
    }

    private fun updateProfileSelection(profile: String) {
        checkSilent.visibility = View.GONE
        checkModerate.visibility = View.GONE
        checkNoisy.visibility = View.GONE
        checkOutdoor.visibility = View.GONE

        when(profile) {
            "silent" -> {
                checkSilent.visibility = View.VISIBLE
                tvSelectedProfile.text = "✓ Perfil seleccionado: Entorno Silencioso"
            }
            "moderate" -> {
                checkModerate.visibility = View.VISIBLE
                tvSelectedProfile.text = "✓ Perfil seleccionado: Entorno Moderado"
            }
            "noisy" -> {
                checkNoisy.visibility = View.VISIBLE
                tvSelectedProfile.text = "✓ Perfil seleccionado: Entorno Ruidoso"
            }
            "outdoor" -> {
                checkOutdoor.visibility = View.VISIBLE
                tvSelectedProfile.text = "✓ Perfil seleccionado: Exterior/Viento"
            }
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("AudioSettings", MODE_PRIVATE)
        val editor = prefs.edit()

        editor.putString("selectedProfile", selectedProfile)

        val profile = profiles[selectedProfile] ?: profiles["moderate"]!!

        editor.putInt("sampleRate", profile.sampleRate)
        editor.putInt("audioSource", profile.audioSource)
        editor.putInt("channelConfig", profile.channelConfig)
        editor.putInt("audioEncoding", profile.audioEncoding)
        editor.putInt("bufferSize", profile.bufferSize)
        editor.putBoolean("agc", profile.agc)
        editor.putBoolean("noiseSuppressor", profile.noiseSuppressor)
        editor.putBoolean("echoCanceler", profile.echoCanceler)
        editor.putBoolean("partialResults", profile.partialResults)
        editor.putBoolean("continuousRecognition", profile.continuousRecognition)
        editor.putInt("maxResults", profile.maxResults)
        editor.putBoolean("autoModeSwitch", profile.autoModeSwitch)
        editor.putBoolean("preferOnline", profile.preferOnline)

        editor.putInt("language", spinnerLanguage.selectedItemPosition)
        editor.putBoolean("vibration", switchVibration.isChecked)
        editor.putBoolean("soundFeedback", switchSoundFeedback.isChecked)

        // Guardar configuración de TTS
        editor.putBoolean("enableTTS", switchTTS.isChecked)
        editor.putInt("ttsSpeed", spinnerTTSSpeed.selectedItemPosition)
        editor.putInt("ttsPitch", spinnerTTSPitch.selectedItemPosition)

        editor.apply()
    }

    private fun saveAndFinish() {
        saveSettings()
        finish()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                saveAndFinish()
            }
        })
    }
}