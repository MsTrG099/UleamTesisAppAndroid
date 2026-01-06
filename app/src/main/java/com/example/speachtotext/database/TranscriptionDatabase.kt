package com.example.speachtotext.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor
import java.text.SimpleDateFormat
import java.util.*

/**
 * Base de datos SQLite para almacenar historial de transcripciones
 */
class TranscriptionDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "transcriptions.db"
        private const val DATABASE_VERSION = 1

        // Tabla de transcripciones
        private const val TABLE_TRANSCRIPTIONS = "transcriptions"

        // Columnas
        private const val COLUMN_ID = "id"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_TEXT = "text"
        private const val COLUMN_WORD_COUNT = "word_count"
        private const val COLUMN_MODE = "mode" // "online" o "offline"
        private const val COLUMN_DURATION_SECONDS = "duration_seconds"
        private const val COLUMN_LANGUAGE = "language"
        private const val COLUMN_SAMPLE_RATE = "sample_rate"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_TRANSCRIPTIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_TEXT TEXT NOT NULL,
                $COLUMN_WORD_COUNT INTEGER NOT NULL,
                $COLUMN_MODE TEXT NOT NULL,
                $COLUMN_DURATION_SECONDS INTEGER NOT NULL,
                $COLUMN_LANGUAGE TEXT,
                $COLUMN_SAMPLE_RATE REAL
            )
        """.trimIndent()

        db?.execSQL(createTable)

        // Índice para búsquedas rápidas por fecha
        db?.execSQL("CREATE INDEX idx_timestamp ON $TABLE_TRANSCRIPTIONS($COLUMN_TIMESTAMP DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TRANSCRIPTIONS")
        onCreate(db)
    }

    /**
     * Inserta una nueva transcripción en la base de datos
     */
    fun insertTranscription(
        text: String,
        mode: String, // "online" o "offline"
        durationSeconds: Long,
        language: String? = null,
        sampleRate: Float? = null
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_TEXT, text)
            put(COLUMN_WORD_COUNT, countWords(text))
            put(COLUMN_MODE, mode)
            put(COLUMN_DURATION_SECONDS, durationSeconds)
            put(COLUMN_LANGUAGE, language)
            put(COLUMN_SAMPLE_RATE, sampleRate)
        }

        val id = db.insert(TABLE_TRANSCRIPTIONS, null, values)
        db.close()
        return id
    }

    /**
     * Obtiene todas las transcripciones ordenadas por fecha (más recientes primero)
     */
    fun getAllTranscriptions(): List<TranscriptionRecord> {
        val transcriptions = mutableListOf<TranscriptionRecord>()
        val db = readableDatabase
        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                transcriptions.add(cursorToTranscription(it))
            }
        }

        db.close()
        return transcriptions
    }

    /**
     * Obtiene transcripciones con paginación
     */
    fun getTranscriptions(limit: Int, offset: Int): List<TranscriptionRecord> {
        val transcriptions = mutableListOf<TranscriptionRecord>()
        val db = readableDatabase
        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC",
            "$offset, $limit"
        )

        cursor?.use {
            while (it.moveToNext()) {
                transcriptions.add(cursorToTranscription(it))
            }
        }

        db.close()
        return transcriptions
    }

    /**
     * Obtiene una transcripción por ID
     */
    fun getTranscriptionById(id: Long): TranscriptionRecord? {
        val db = readableDatabase
        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        var transcription: TranscriptionRecord? = null
        cursor?.use {
            if (it.moveToFirst()) {
                transcription = cursorToTranscription(it)
            }
        }

        db.close()
        return transcription
    }

    /**
     * Busca transcripciones que contengan un texto específico
     */
    fun searchTranscriptions(searchQuery: String): List<TranscriptionRecord> {
        val transcriptions = mutableListOf<TranscriptionRecord>()
        val db = readableDatabase
        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            "$COLUMN_TEXT LIKE ?",
            arrayOf("%$searchQuery%"),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                transcriptions.add(cursorToTranscription(it))
            }
        }

        db.close()
        return transcriptions
    }

    /**
     * Obtiene transcripciones por modo (online/offline)
     */
    fun getTranscriptionsByMode(mode: String): List<TranscriptionRecord> {
        val transcriptions = mutableListOf<TranscriptionRecord>()
        val db = readableDatabase
        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            "$COLUMN_MODE = ?",
            arrayOf(mode),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                transcriptions.add(cursorToTranscription(it))
            }
        }

        db.close()
        return transcriptions
    }

    /**
     * Obtiene transcripciones en un rango de fechas
     */
    fun getTranscriptionsByDateRange(startTimestamp: Long, endTimestamp: Long): List<TranscriptionRecord> {
        val transcriptions = mutableListOf<TranscriptionRecord>()
        val db = readableDatabase
        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            "$COLUMN_TIMESTAMP BETWEEN ? AND ?",
            arrayOf(startTimestamp.toString(), endTimestamp.toString()),
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                transcriptions.add(cursorToTranscription(it))
            }
        }

        db.close()
        return transcriptions
    }

    /**
     * NUEVO: Búsqueda avanzada combinando múltiples filtros
     */
    fun searchTranscriptionsAdvanced(
        keyword: String? = null,
        mode: String? = null,
        startDate: Long? = null,
        endDate: Long? = null
    ): List<TranscriptionRecord> {
        val transcriptions = mutableListOf<TranscriptionRecord>()
        val db = readableDatabase

        // Construir condiciones WHERE dinámicamente
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        // Filtro por palabra clave
        if (!keyword.isNullOrBlank()) {
            conditions.add("$COLUMN_TEXT LIKE ?")
            args.add("%$keyword%")
        }

        // Filtro por modo
        if (!mode.isNullOrBlank() && mode != "todos") {
            conditions.add("$COLUMN_MODE = ?")
            args.add(mode)
        }

        // Filtro por rango de fechas
        if (startDate != null && endDate != null) {
            conditions.add("$COLUMN_TIMESTAMP BETWEEN ? AND ?")
            args.add(startDate.toString())
            args.add(endDate.toString())
        }

        val whereClause = if (conditions.isNotEmpty()) {
            conditions.joinToString(" AND ")
        } else {
            null
        }

        val cursor: Cursor? = db.query(
            TABLE_TRANSCRIPTIONS,
            null,
            whereClause,
            if (args.isNotEmpty()) args.toTypedArray() else null,
            null,
            null,
            "$COLUMN_TIMESTAMP DESC"
        )

        cursor?.use {
            while (it.moveToNext()) {
                transcriptions.add(cursorToTranscription(it))
            }
        }

        db.close()
        return transcriptions
    }

    /**
     * NUEVO: Obtiene transcripciones de hoy
     */
    fun getTodayTranscriptions(): List<TranscriptionRecord> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_MONTH, 1)
        val endOfDay = calendar.timeInMillis - 1

        return getTranscriptionsByDateRange(startOfDay, endOfDay)
    }

    /**
     * NUEVO: Obtiene transcripciones de esta semana
     */
    fun getThisWeekTranscriptions(): List<TranscriptionRecord> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.WEEK_OF_YEAR, 1)
        val endOfWeek = calendar.timeInMillis - 1

        return getTranscriptionsByDateRange(startOfWeek, endOfWeek)
    }

    /**
     * NUEVO: Obtiene transcripciones de este mes
     */
    fun getThisMonthTranscriptions(): List<TranscriptionRecord> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 1)
        val endOfMonth = calendar.timeInMillis - 1

        return getTranscriptionsByDateRange(startOfMonth, endOfMonth)
    }

    /**
     * Elimina una transcripción por ID
     */
    fun deleteTranscription(id: Long): Int {
        val db = writableDatabase
        val result = db.delete(TABLE_TRANSCRIPTIONS, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
        return result
    }

    /**
     * Elimina todas las transcripciones
     */
    fun deleteAllTranscriptions(): Int {
        val db = writableDatabase
        val result = db.delete(TABLE_TRANSCRIPTIONS, null, null)
        db.close()
        return result
    }

    /**
     * ACTUALIZADO: Obtiene estadísticas completas del historial
     */
    fun getStatistics(): TranscriptionStatistics {
        val db = readableDatabase

        var totalCount = 0
        var onlineCount = 0
        var offlineCount = 0
        var totalWords = 0
        var totalDuration = 0L
        var oldestTimestamp = 0L
        var newestTimestamp = 0L

        val cursor = db.rawQuery("""
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN $COLUMN_MODE = 'online' THEN 1 ELSE 0 END) as online_count,
                SUM(CASE WHEN $COLUMN_MODE = 'offline' THEN 1 ELSE 0 END) as offline_count,
                SUM($COLUMN_WORD_COUNT) as total_words,
                SUM($COLUMN_DURATION_SECONDS) as total_duration,
                MIN($COLUMN_TIMESTAMP) as oldest,
                MAX($COLUMN_TIMESTAMP) as newest
            FROM $TABLE_TRANSCRIPTIONS
        """, null)

        cursor?.use {
            if (it.moveToFirst()) {
                totalCount = it.getInt(0)
                onlineCount = it.getInt(1)
                offlineCount = it.getInt(2)
                totalWords = it.getInt(3)
                totalDuration = it.getLong(4)
                oldestTimestamp = it.getLong(5)
                newestTimestamp = it.getLong(6)
            }
        }

        db.close()

        return TranscriptionStatistics(
            totalCount = totalCount,
            onlineCount = onlineCount,
            offlineCount = offlineCount,
            totalWords = totalWords,
            totalDurationSeconds = totalDuration,
            averageWordsPerTranscription = if (totalCount > 0) totalWords / totalCount else 0,
            averageDurationSeconds = if (totalCount > 0) totalDuration / totalCount else 0,
            oldestTimestamp = oldestTimestamp,
            newestTimestamp = newestTimestamp
        )
    }

    /**
     * Convierte un cursor a un objeto TranscriptionRecord
     */
    private fun cursorToTranscription(cursor: Cursor): TranscriptionRecord {
        return TranscriptionRecord(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
            text = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TEXT)),
            wordCount = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_WORD_COUNT)),
            mode = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MODE)),
            durationSeconds = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_DURATION_SECONDS)),
            language = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LANGUAGE)),
            sampleRate = if (cursor.isNull(cursor.getColumnIndexOrThrow(COLUMN_SAMPLE_RATE))) null
            else cursor.getFloat(cursor.getColumnIndexOrThrow(COLUMN_SAMPLE_RATE))
        )
    }

    /**
     * Cuenta las palabras en un texto
     */
    private fun countWords(text: String): Int {
        if (text.isBlank()) return 0
        return text.trim().split("\\s+".toRegex()).size
    }
}

/**
 * Clase de datos para representar una transcripción
 */
data class TranscriptionRecord(
    val id: Long,
    val timestamp: Long,
    val text: String,
    val wordCount: Int,
    val mode: String, // "online" o "offline"
    val durationSeconds: Long,
    val language: String?,
    val sampleRate: Float?
) {
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun getFormattedDuration(): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60

        return when {
            hours > 0 -> String.format("%02d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%02d:%02d", minutes, seconds)
            else -> String.format("0:%02d", seconds)
        }
    }

    fun getPreviewText(maxLength: Int = 100): String {
        return if (text.length > maxLength) {
            text.substring(0, maxLength) + "..."
        } else {
            text
        }
    }
}

/**
 * ACTUALIZADA: Clase de datos para estadísticas completas
 */
data class TranscriptionStatistics(
    val totalCount: Int = 0,
    val totalWords: Int = 0,
    val totalDurationSeconds: Long = 0,
    val onlineCount: Int = 0,
    val offlineCount: Int = 0,
    val averageWordsPerTranscription: Int = 0,
    val averageDurationSeconds: Long = 0,
    val oldestTimestamp: Long = 0,
    val newestTimestamp: Long = 0
) {
    fun getFormattedTotalDuration(): String {
        val hours = totalDurationSeconds / 3600
        val minutes = (totalDurationSeconds % 3600) / 60
        val seconds = totalDurationSeconds % 60

        return when {
            hours > 0 -> String.format("%dh %02dm %02ds", hours, minutes, seconds)
            minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    fun getFormattedAverageDuration(): String {
        val hours = averageDurationSeconds / 3600
        val minutes = (averageDurationSeconds % 3600) / 60
        val seconds = averageDurationSeconds % 60

        return when {
            hours > 0 -> String.format("%dh %02dm %02ds", hours, minutes, seconds)
            minutes > 0 -> String.format("%dm %02ds", minutes, seconds)
            else -> String.format("%ds", seconds)
        }
    }

    fun getOnlinePercentage(): Int {
        return if (totalCount > 0) (onlineCount * 100) / totalCount else 0
    }

    fun getOfflinePercentage(): Int {
        return if (totalCount > 0) (offlineCount * 100) / totalCount else 0
    }
}