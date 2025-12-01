package com.example.speachtotext

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.speachtotext.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
    }

    private fun setupButtons() {
        // Botón: Iniciar Transcripción
        binding.btnStartTranscription.setOnClickListener {
            val intent = Intent(this, TranscriptionActivity::class.java)
            startActivity(intent)
        }

        // Botón: Home (ya estamos en home, no hace nada)
        findViewById<View>(R.id.btnBottomHome).setOnClickListener {
            // Ya estamos en home, opcionalmente mostrar un mensaje
            Toast.makeText(this, "Ya estás en Inicio", Toast.LENGTH_SHORT).show()
        }

        // Botón: Historial
        findViewById<View>(R.id.btnHistory).setOnClickListener {
            val intent = Intent(this, HistoryActivity::class.java)
            startActivity(intent)
        }

        // Botón: Configuración/Ajustes
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }
}