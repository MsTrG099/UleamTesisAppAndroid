package com.example.speachtotext

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.speachtotext.database.TranscriptionDatabase
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException
import java.util.*

class TranscriptionActivity : AppCompatActivity() {

    // Views existentes
    private lateinit var btnBack: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var btnToggleMode: Button
    private lateinit var tvResult: TextView
    private lateinit var btnRecord: Button
    private lateinit var btnClear: Button
    private lateinit var btnSettings: ImageButton

    // NUEVO: Botón de reproducir TTS
    private lateinit var btnReplayTTS: ImageButton

    // Vosk (Offline)
    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null

    // Google SpeechRecognizer (Online)
    private var googleSpeechRecognizer: SpeechRecognizer? = null

    // Text-to-Speech (TTS)
    private var textToSpeech: TextToSpeech? = null
    private var isTTSInitialized = false
    private var isTTSSpeaking = false

    private var isListening = false
    private var isOnlineMode = false
    private var isManualMode = false

    // Helper de configuración
    private lateinit var audioSettings: AudioSettingsHelper

    // Para efectos de audio
    private var automaticGainControl: android.media.audiofx.AutomaticGainControl? = null
    private var noiseSuppressor: android.media.audiofx.NoiseSuppressor? = null
    private var echoCanceler: android.media.audiofx.AcousticEchoCanceler? = null

    // Para vibración
    private lateinit var vibrator: Vibrator

    // Para el historial
    private lateinit var database: TranscriptionDatabase
    private var recordingStartTime: Long = 0

    companion object {
        private const val TAG = "TranscriptionActivity"
        private const val REQUEST_RECORD_AUDIO = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transcription)

        // Inicializar helper de configuración y vibrador
        audioSettings = AudioSettingsHelper(this)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Inicializar base de datos
        database = TranscriptionDatabase(this)

        // Inicializar Text-to-Speech
        initTextToSpeech()

        // Inicializar vistas
        initViews()

        // Aplicar tamaño de texto inicial
        applyTextSize()

        // Configurar listeners
        setupListeners()

        // Verificar permisos
        checkPermissions()

        // Inicializar modo
        checkConnectionAndSetMode()

        // Inicializar modelo Vosk en segundo plano
        initVoskModel()

        // Log de configuración actual (debug)
        audioSettings.logCurrentSettings(TAG)

        // NUEVO: Actualizar visibilidad del botón TTS
        updateReplayButtonVisibility()
    }

    override fun onResume() {
        super.onResume()
        // Recargar configuración por si cambió en Settings
        audioSettings.logCurrentSettings(TAG)
        configureTTS()
        applyTextSize()

        // Re-evaluar el modo si no está en manual
        if (!isManualMode && !isListening) {
            checkConnectionAndSetMode()
        }

        // NUEVO: Actualizar visibilidad del botón TTS
        updateReplayButtonVisibility()
    }

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageCode = audioSettings.getLanguageCode()
                val locale = when {
                    languageCode.startsWith("es") -> Locale("es", "ES")
                    languageCode.startsWith("en") -> Locale.ENGLISH
                    else -> Locale("es", "ES")
                }

                val result = textToSpeech?.setLanguage(locale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: Idioma no soportado")
                    NotificationHelper.show(this, "⚠️ Idioma TTS no disponible")
                    isTTSInitialized = false
                } else {
                    isTTSInitialized = true
                    configureTTS()
                    setupTTSListener()
                    Log.d(TAG, "✓ TTS inicializado correctamente")
                }
            } else {
                Log.e(TAG, "✗ Error al inicializar TTS")
                isTTSInitialized = false
            }
        }
    }

    private fun setupTTSListener() {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isTTSSpeaking = true
                Log.d(TAG, "🔊 TTS comenzó a hablar")
                runOnUiThread {
                    tvStatus.text = "🔊 Reproduciendo texto..."
                    updateReplayButtonState(true) // NUEVO: Deshabilitar mientras habla
                }
            }

            override fun onDone(utteranceId: String?) {
                isTTSSpeaking = false
                Log.d(TAG, "✓ TTS terminó de hablar")
                runOnUiThread {
                    val mode = if (isOnlineMode) "Online" else "Offline"
                    tvStatus.text = "Pausado ($mode) - Presiona Grabar para continuar"
                    updateReplayButtonState(false) // NUEVO: Rehabilitar
                }
            }

            override fun onError(utteranceId: String?) {
                isTTSSpeaking = false
                Log.e(TAG, "✗ Error en TTS")
                runOnUiThread {
                    val mode = if (isOnlineMode) "Online" else "Offline"
                    tvStatus.text = "Error TTS - Pausado ($mode)"
                    updateReplayButtonState(false) // NUEVO: Rehabilitar
                }
            }
        })
    }

    private fun configureTTS() {
        if (!isTTSInitialized || textToSpeech == null) return

        val speed = audioSettings.getTTSSpeed()
        textToSpeech?.setSpeechRate(speed)

        val pitch = audioSettings.getTTSPitch()
        textToSpeech?.setPitch(pitch)

        Log.d(TAG, "TTS configurado: velocidad=$speed, tono=$pitch")
    }

    private fun applyTextSize() {
        val textSize = audioSettings.getTranscriptionTextSize()
        tvResult.textSize = textSize
        Log.d(TAG, "Tamaño de texto aplicado: ${textSize}sp")
    }

    private fun speakText(text: String) {
        if (!audioSettings.isTTSEnabled()) {
            Log.d(TAG, "TTS deshabilitado en configuración")
            return
        }

        if (!isTTSInitialized || textToSpeech == null) {
            Log.w(TAG, "TTS no inicializado, no se puede leer el texto")
            return
        }

        if (text.isBlank() || text == "El texto aparecerá aquí...") {
            Log.d(TAG, "Texto vacío o placeholder, no se lee")
            return
        }

        // Detener el reconocimiento antes de hablar
        if (isListening) {
            Log.d(TAG, "⏸️ Deteniendo reconocimiento para reproducir TTS")
            stopListeningForTTS()
        }

        // Detener cualquier lectura en progreso
        textToSpeech?.stop()

        // Leer el texto con ID único para el listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val params = Bundle()
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "TranscriptionTTS_${System.currentTimeMillis()}")
        } else {
            @Suppress("DEPRECATION")
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }

        Log.d(TAG, "🔊 Leyendo transcripción: ${text.take(50)}...")
    }

    // CRÍTICO: Detener reconocimiento Y guardar en historial
    private fun stopListeningForTTS() {
        Log.d(TAG, "⏸️ stopListeningForTTS() - Deteniendo y guardando")

        voskSpeechService?.stop()
        googleSpeechRecognizer?.cancel()

        isListening = false
        updateRecordButtonState()

        vibrateIfEnabled()

        // SOLUCIÓN: Guardar en historial cuando TTS detiene el reconocimiento
        saveTranscriptionToHistory()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvStatus = findViewById(R.id.tvStatus)
        btnToggleMode = findViewById(R.id.btnToggleMode)
        tvResult = findViewById(R.id.tvResult)
        btnRecord = findViewById(R.id.btnRecord)
        btnClear = findViewById(R.id.btnClear)
        btnSettings = findViewById(R.id.btnSettings)

        // NUEVO: Inicializar botón de reproducir TTS
        btnReplayTTS = findViewById(R.id.btnReplayTTS)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnRecord.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                if (isTTSSpeaking) {
                    Toast.makeText(this, "⏸️ Espera a que termine de leer el texto", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                tvResult.text = ""
                startListening()
            }
        }

        btnClear.setOnClickListener {
            tvResult.text = ""
            textToSpeech?.stop()
            isTTSSpeaking = false
            updateReplayButtonVisibility() // NUEVO: Actualizar visibilidad
        }

        btnToggleMode.setOnClickListener {
            stopListening()
            toggleMode()
        }

        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // NUEVO: Listener del botón de reproducir TTS
        btnReplayTTS.setOnClickListener {
            if (isTTSSpeaking) {
                Toast.makeText(this, "⏸️ Ya se está reproduciendo el audio", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val text = tvResult.text.toString()
            if (text.isNotBlank() && text != "El texto aparecerá aquí...") {
                vibrateIfEnabled()
                speakText(text)
            }
        }
    }

    // NUEVO: Actualizar visibilidad del botón de replay según configuración TTS y contenido
    private fun updateReplayButtonVisibility() {
        val ttsEnabled = audioSettings.isTTSEnabled()
        val hasText = tvResult.text.toString().let {
            it.isNotBlank() && it != "El texto aparecerá aquí..."
        }

        btnReplayTTS.visibility = if (ttsEnabled && hasText) {
            android.view.View.VISIBLE
        } else {
            android.view.View.GONE
        }

        Log.d(TAG, "Botón Replay TTS: visible=${btnReplayTTS.visibility == android.view.View.VISIBLE}, TTS=$ttsEnabled, texto=$hasText")
    }

    // NUEVO: Actualizar estado del botón (habilitado/deshabilitado) mientras TTS habla
    private fun updateReplayButtonState(speaking: Boolean) {
        btnReplayTTS.isEnabled = !speaking
        btnReplayTTS.alpha = if (speaking) 0.5f else 1.0f
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun checkConnectionAndSetMode() {
        if (!isManualMode && audioSettings.isAutoModeSwitchEnabled()) {
            val hasInternet = isInternetAvailable()
            isOnlineMode = hasInternet && audioSettings.shouldPreferOnline()
            updateModeUI()

            if (isOnlineMode) {
                Toast.makeText(this, "✓ Conectado - Modo Online (Google)", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Modo Online activado automáticamente")
            } else {
                Toast.makeText(this, "✗ Sin conexión - Modo Offline (Vosk)", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Modo Offline activado automáticamente")
            }
        } else if (isManualMode) {
            updateModeUI()
        }
    }

    private fun toggleMode() {
        isManualMode = true
        isOnlineMode = !isOnlineMode

        updateModeUI()

        val modeText = if (isOnlineMode) "Online (Google)" else "Offline (Vosk)"
        Toast.makeText(this, "Modo cambiado a: $modeText", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Modo manual activado: $modeText")
    }

    private fun updateModeUI() {
        if (isOnlineMode) {
            tvStatus.text = "Modo Online 🌐 (Google)"
            btnRecord.text = "🎤 Grabar (Online)"
            btnToggleMode.text = "Cambiar a Offline"
        } else {
            tvStatus.text = "Modo Offline 📡 (Vosk)"
            btnRecord.text = "🎤 Grabar (Offline)"
            btnToggleMode.text = "Cambiar a Online"
        }
    }

    private fun initVoskModel() {
        tvStatus.text = "Cargando modelo offline..."
        Log.d(TAG, "Iniciando carga del modelo Vosk...")

        Thread {
            try {
                StorageService.unpack(
                    this,
                    "model-es",
                    "model",
                    { loadedModel: Model ->
                        this.voskModel = loadedModel
                        runOnUiThread {
                            updateModeUI()
                            Toast.makeText(
                                this,
                                "Modelo Vosk cargado correctamente",
                                Toast.LENGTH_SHORT
                            ).show()
                            Log.d(TAG, "Modelo Vosk cargado exitosamente")
                        }
                    },
                    { exception: IOException ->
                        runOnUiThread {
                            tvStatus.text = "Error al cargar modelo ❌"
                            val errorMsg = "Error: ${exception.message}"
                            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
                            Log.e(TAG, "Error cargando modelo Vosk", exception)
                        }
                    }
                )
            } catch (e: Exception) {
                runOnUiThread {
                    tvStatus.text = "Error crítico ❌"
                    Toast.makeText(
                        this,
                        "Error crítico: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e(TAG, "Error crítico al inicializar modelo", e)
                }
            }
        }.start()
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso de micrófono requerido", Toast.LENGTH_SHORT).show()
            checkPermissions()
            return
        }

        releaseAudioEffects()

        recordingStartTime = System.currentTimeMillis()
        Log.d(TAG, "📝 Inicio de grabación: $recordingStartTime")

        if (isTTSSpeaking) {
            textToSpeech?.stop()
            isTTSSpeaking = false
        }

        if (isOnlineMode) {
            startGoogleSpeechRecognition()
        } else {
            startVoskRecognition()
        }
    }

    private fun startVoskRecognition() {
        if (voskModel == null) {
            Toast.makeText(this, "El modelo Vosk aún no está cargado", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "Intento de grabar sin modelo cargado")
            return
        }

        try {
            val sampleRate = audioSettings.getSampleRate().toFloat()

            Log.d(TAG, "Iniciando Vosk con Sample Rate: $sampleRate Hz")
            val recognizer = Recognizer(voskModel, sampleRate)
            voskSpeechService = SpeechService(recognizer, sampleRate)
            voskSpeechService?.startListening(voskRecognitionListener)

            isListening = true
            updateRecordButtonState()
            tvStatus.text = "Escuchando... 🎤 (Offline)"
            Log.d(TAG, "Reconocimiento Vosk iniciado")

            vibrateIfEnabled()

        } catch (e: IOException) {
            val errorMsg = "Error al iniciar: ${e.message}"
            Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error al iniciar reconocimiento Vosk", e)
        }
    }

    private fun startGoogleSpeechRecognition() {
        if (!isInternetAvailable() && !isManualMode) {
            NotificationHelper.show(this, "Sin conexión. Cambiando a modo Offline...")
            isOnlineMode = false
            updateModeUI()
            startVoskRecognition()
            return
        }

        try {
            Log.d(TAG, "Iniciando reconocimiento Google...")

            googleSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            googleSpeechRecognizer?.setRecognitionListener(googleRecognitionListener)

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, audioSettings.getLanguageCode())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, audioSettings.isPartialResultsEnabled())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, audioSettings.getMaxResults())
            }

            googleSpeechRecognizer?.startListening(intent)

            isListening = true
            updateRecordButtonState()
            tvStatus.text = "Escuchando... 🎤 (Online)"
            Log.d(TAG, "Reconocimiento Google iniciado")

            vibrateIfEnabled()

        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error al iniciar reconocimiento Google", e)
        }
    }

    private fun stopListening() {
        Log.d(TAG, "⏹️ stopListening() - Deteniendo y guardando")

        voskSpeechService?.stop()
        voskSpeechService?.shutdown()
        voskSpeechService = null

        googleSpeechRecognizer?.stopListening()
        googleSpeechRecognizer?.destroy()
        googleSpeechRecognizer = null

        releaseAudioEffects()

        isListening = false
        updateRecordButtonState()

        val mode = if (isOnlineMode) "Online" else "Offline"
        tvStatus.text = "Detenido ($mode)"

        vibrateIfEnabled()

        saveTranscriptionToHistory()
    }

    private fun saveTranscriptionToHistory() {
        val text = tvResult.text.toString().trim()

        if (text.isBlank() || text == "El texto aparecerá aquí...") {
            Log.d(TAG, "❌ No se guarda: texto vacío o placeholder")
            return
        }

        val durationMillis = System.currentTimeMillis() - recordingStartTime
        val durationSeconds = durationMillis / 1000
        val mode = if (isOnlineMode) "online" else "offline"
        val language = audioSettings.getLanguageCode()
        val sampleRate = audioSettings.getSampleRate()

        try {
            val id = database.insertTranscription(
                text = text,
                mode = mode,
                durationSeconds = durationSeconds,
                language = language,
                sampleRate = sampleRate.toFloat()
            )

            Log.d(TAG, "✅ Guardado en historial [ID: $id, Modo: $mode, Duración: ${durationSeconds}s, Palabras: ${countWords(text)}]")
            Log.d(TAG, "   Texto guardado: '${text.take(50)}...'")

            NotificationHelper.show(
                this,
                "✓ Guardado (${countWords(text)} palabras)"
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al guardar en historial", e)
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).size
    }

    private fun updateRecordButtonState() {
        if (isListening) {
            btnRecord.text = "⏹ Detener"
            btnRecord.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_red_dark)
            )
        } else {
            val mode = if (isOnlineMode) "Online" else "Offline"
            btnRecord.text = "🎤 Grabar ($mode)"
            btnRecord.setBackgroundColor(
                ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            )
        }
    }

    private fun vibrateIfEnabled() {
        if (!audioSettings.isVibrationEnabled()) {
            Log.d(TAG, "📴 Vibración desactivada - no vibra")
            return
        }

        Log.d(TAG, "📳 Vibrando")
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

    private fun setupAudioEffects(audioSessionId: Int) {
        releaseAudioEffects()

        if (audioSessionId == 0) return

        if (audioSettings.isAGCEnabled() &&
            android.media.audiofx.AutomaticGainControl.isAvailable()) {
            automaticGainControl = android.media.audiofx.AutomaticGainControl.create(audioSessionId)
            automaticGainControl?.enabled = true
            Log.d(TAG, "AGC activado para sesión: $audioSessionId")
        }

        if (audioSettings.isNoiseSuppressorEnabled() &&
            android.media.audiofx.NoiseSuppressor.isAvailable()) {
            noiseSuppressor = android.media.audiofx.NoiseSuppressor.create(audioSessionId)
            noiseSuppressor?.enabled = true
            Log.d(TAG, "Supresión de ruido activada para sesión: $audioSessionId")
        }

        if (audioSettings.isEchoCancelerEnabled() &&
            android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
            echoCanceler = android.media.audiofx.AcousticEchoCanceler.create(audioSessionId)
            echoCanceler?.enabled = true
            Log.d(TAG, "Cancelación de eco activada para sesión: $audioSessionId")
        }
    }

    private fun releaseAudioEffects() {
        automaticGainControl?.release()
        automaticGainControl = null
        noiseSuppressor?.release()
        noiseSuppressor = null
        echoCanceler?.release()
        echoCanceler = null
    }

    private val voskRecognitionListener = object : org.vosk.android.RecognitionListener {
        override fun onResult(hypothesis: String?) {
            Log.d(TAG, "Vosk onResult: $hypothesis")
            if (!hypothesis.isNullOrEmpty()) {
                try {
                    val text = hypothesis
                        .substringAfter("\"text\" : \"")
                        .substringBefore("\"")

                    if (text.isNotEmpty()) {
                        runOnUiThread {
                            val currentText = tvResult.text.toString()
                            tvResult.text = if (currentText.isEmpty() || currentText == "El texto aparecerá aquí...") {
                                text
                            } else {
                                "$currentText $text"
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando resultado Vosk", e)
                }
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            Log.d(TAG, "Vosk onFinalResult: $hypothesis")
            onResult(hypothesis)
            runOnUiThread {
                tvStatus.text = "Listo ✓ (Offline)"

                val finalText = tvResult.text.toString()
                speakText(finalText)
                updateReplayButtonVisibility() // NUEVO: Actualizar visibilidad
            }
        }

        override fun onPartialResult(hypothesis: String?) {
            if (!hypothesis.isNullOrEmpty()) {
                try {
                    val text = hypothesis
                        .substringAfter("\"partial\" : \"")
                        .substringBefore("\"")

                    if (text.isNotEmpty()) {
                        runOnUiThread {
                            tvStatus.text = "Detectando: $text"
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parseando resultado parcial Vosk", e)
                }
            }
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Error en reconocimiento Vosk", exception)
            runOnUiThread {
                tvStatus.text = "Error: ${exception?.message}"
                Toast.makeText(
                    this@TranscriptionActivity,
                    "Error: ${exception?.message}",
                    Toast.LENGTH_SHORT
                ).show()
                isListening = false
                updateRecordButtonState()
            }
        }

        override fun onTimeout() {
            Log.d(TAG, "Timeout en reconocimiento Vosk")
            runOnUiThread {
                tvStatus.text = "Tiempo agotado (Offline)"
                stopListening()
            }
        }
    }

    private val googleRecognitionListener = object : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Google: Ready for speech")
            runOnUiThread {
                tvStatus.text = "Listo para hablar... 🎤 (Online)"
            }
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Google: Beginning of speech")
            runOnUiThread {
                tvStatus.text = "Escuchando... 🎤 (Online)"
            }
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "Google: End of speech")
            runOnUiThread {
                tvStatus.text = "Procesando... (Online)"
            }
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
                SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permisos insuficientes"
                SpeechRecognizer.ERROR_NETWORK -> "Error de red"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
                SpeechRecognizer.ERROR_NO_MATCH -> "No se reconoció voz"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocedor ocupado"
                SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout de voz"
                else -> "Error desconocido: $error"
            }

            Log.e(TAG, "Google error: $errorMessage")
            runOnUiThread {
                tvStatus.text = "Error: $errorMessage"
                Toast.makeText(
                    this@TranscriptionActivity,
                    errorMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }

            if (isListening && !isTTSSpeaking) {
                Log.d(TAG, "Reintentando escucha tras error...")
                googleSpeechRecognizer?.cancel()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, audioSettings.getLanguageCode())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, audioSettings.isPartialResultsEnabled())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, audioSettings.getMaxResults())
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Long.MAX_VALUE)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, Long.MAX_VALUE)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, Long.MAX_VALUE)
                }
                googleSpeechRecognizer?.startListening(intent)
            } else {
                Log.d(TAG, "Escucha detenida, no se reinicia.")
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "Google onResults: $text")

                runOnUiThread {
                    val currentText = tvResult.text.toString()
                    tvResult.text = if (currentText.isEmpty() || currentText == "El texto aparecerá aquí...") {
                        text
                    } else {
                        "$currentText $text"
                    }
                    tvStatus.text = "Listo ✓ (Online)"

                    speakText(text)
                    updateReplayButtonVisibility() // NUEVO: Actualizar visibilidad
                }
            }

            if (isListening && !isTTSSpeaking && !audioSettings.isTTSEnabled()) {
                Log.d(TAG, "Reiniciando escucha continua (TTS deshabilitado)...")
                googleSpeechRecognizer?.cancel()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, audioSettings.getLanguageCode())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, audioSettings.isPartialResultsEnabled())
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, audioSettings.getMaxResults())
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Long.MAX_VALUE)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, Long.MAX_VALUE)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, Long.MAX_VALUE)
                }
                googleSpeechRecognizer?.startListening(intent)
            } else {
                Log.d(TAG, "Escucha pausada (TTS activo o detenida manualmente).")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                Log.d(TAG, "Google partial: $text")
                runOnUiThread {
                    tvStatus.text = "Detectando: $text"
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permiso concedido", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Destruyendo actividad...")

        voskSpeechService?.stop()
        voskSpeechService?.shutdown()
        googleSpeechRecognizer?.destroy()
        releaseAudioEffects()

        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        Log.d(TAG, "✓ TTS liberado")
    }
}