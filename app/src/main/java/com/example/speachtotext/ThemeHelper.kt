package com.example.speachtotext

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Helper para aplicar el tema de la aplicación (claro/oscuro/seguir sistema)
 */
object ThemeHelper {

    /**
     * Aplica el tema guardado en las preferencias
     * Debe llamarse en onCreate() de cada Activity ANTES de setContentView()
     */
    fun applyTheme(context: Context) {
        val prefs = context.getSharedPreferences("AudioSettings", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("themeMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    /**
     * Guarda y aplica un nuevo tema
     * @param context Contexto de la aplicación
     * @param themeIndex Índice del tema: 0=Claro, 1=Oscuro, 2=Seguir sistema
     */
    fun saveAndApplyTheme(context: Context, themeIndex: Int) {
        val mode = when(themeIndex) {
            0 -> AppCompatDelegate.MODE_NIGHT_NO          // Modo Claro
            1 -> AppCompatDelegate.MODE_NIGHT_YES         // Modo Oscuro
            2 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM  // Seguir sistema
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }

        // Guardar preferencia
        val prefs = context.getSharedPreferences("AudioSettings", Context.MODE_PRIVATE)
        prefs.edit().putInt("themeMode", mode).apply()

        // Aplicar tema inmediatamente
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    /**
     * Obtiene el índice del tema actual
     * @return 0=Claro, 1=Oscuro, 2=Seguir sistema
     */
    fun getCurrentThemeIndex(context: Context): Int {
        val prefs = context.getSharedPreferences("AudioSettings", Context.MODE_PRIVATE)
        val mode = prefs.getInt("themeMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        return when(mode) {
            AppCompatDelegate.MODE_NIGHT_NO -> 0
            AppCompatDelegate.MODE_NIGHT_YES -> 1
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> 2
            else -> 2
        }
    }
}