package com.example.speachtotext

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.speachtotext.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Configurar botones
        setupButtons()
    }

    private fun setupButtons() {
        // Botón: Iniciar Transcripción
        binding.btnStartTranscription.setOnClickListener {
            val intent = Intent(this, TranscriptionActivity::class.java)
            startActivity(intent)
        }

        // Botón: Historial - ✅ AHORA FUNCIONAL
        binding.btnHistory.setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Botón: Configuración
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}