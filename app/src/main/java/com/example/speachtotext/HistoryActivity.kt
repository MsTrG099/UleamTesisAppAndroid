package com.example.speachtotext

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.speachtotext.database.TranscriptionDatabase
import com.example.speachtotext.database.TranscriptionRecord
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HistoryAdapter
    private lateinit var database: TranscriptionDatabase
    private lateinit var tvEmpty: TextView
    private lateinit var tvCounter: TextView

    // Elementos de búsqueda y filtros
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var spinnerMode: Spinner
    private lateinit var btnDateFilter: Button
    private lateinit var btnClearFilters: Button
    private lateinit var btnStatistics: Button

    // Variables para filtros
    private var currentSearchQuery: String = ""
    private var currentMode: String = "Todos" // Todos, Online, Offline
    private var dateFilterStartMillis: Long? = null
    private var dateFilterEndMillis: Long? = null
    private var dateFilterType: String = "" // "today", "week", "month", "custom", ""

    private var allRecords: List<TranscriptionRecord> = emptyList()

    companion object {
        private const val TAG = "HistoryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        database = TranscriptionDatabase(this)

        // Inicializar vistas con los IDs CORRECTOS de tu XML
        recyclerView = findViewById(R.id.recyclerViewHistory)
        tvEmpty = findViewById(R.id.tvEmptyState)
        tvCounter = findViewById(R.id.tvTranscriptionCount)

        // Inicializar elementos de búsqueda y filtros
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        spinnerMode = findViewById(R.id.spinnerMode)
        btnDateFilter = findViewById(R.id.btnDateFilter)
        btnClearFilters = findViewById(R.id.btnClearFilters)
        btnStatistics = findViewById(R.id.btnShowStats)

        // Configurar RecyclerView
        adapter = HistoryAdapter(emptyList(),
            onItemClick = { record -> showDetailDialog(record) },
            onDeleteClick = { record -> confirmDelete(record) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        setupListeners()
        setupModeSpinner()
        loadTranscriptions()

        // Navegación inferior con los IDs CORRECTOS
        findViewById<LinearLayout>(R.id.btnBottomHome).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.btnBottomHistory).setOnClickListener {
            // Ya estamos aquí
        }
        findViewById<LinearLayout>(R.id.btnBottomSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Botón back en header
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        // Búsqueda en tiempo real
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString() ?: ""
                btnClearSearch.visibility = if (currentSearchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
        })

        // Botón limpiar búsqueda
        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
        }

        // Botón filtrar por fecha
        btnDateFilter.setOnClickListener {
            showDateRangeDialog()
        }

        // Botón limpiar todos los filtros
        btnClearFilters.setOnClickListener {
            clearAllFilters()
        }

        // Botón mostrar estadísticas
        btnStatistics.setOnClickListener {
            showStatisticsDialog()
        }
    }

    private fun setupModeSpinner() {
        val modes = arrayOf("Todos", "Online", "Offline")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modes)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMode.adapter = spinnerAdapter

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMode = modes[position]
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadTranscriptions() {
        allRecords = database.getAllTranscriptions()
        applyFilters()
    }

    private fun applyFilters() {
        var filteredRecords = allRecords

        // Aplicar filtro de búsqueda (usa "text" en lugar de "transcription")
        if (currentSearchQuery.isNotEmpty()) {
            filteredRecords = filteredRecords.filter {
                it.text.contains(currentSearchQuery, ignoreCase = true)
            }
        }

        // Aplicar filtro de modo (usa "mode" que es String, no Boolean)
        when (currentMode) {
            "Online" -> filteredRecords = filteredRecords.filter { it.mode == "online" }
            "Offline" -> filteredRecords = filteredRecords.filter { it.mode == "offline" }
        }

        // Aplicar filtro de fecha
        if (dateFilterStartMillis != null && dateFilterEndMillis != null) {
            filteredRecords = filteredRecords.filter { record ->
                record.timestamp >= dateFilterStartMillis!! && record.timestamp <= dateFilterEndMillis!!
            }
        }

        updateUI(filteredRecords)
    }

    private fun updateUI(records: List<TranscriptionRecord>) {
        if (records.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmpty.visibility = View.VISIBLE

            // Mensaje diferente si hay filtros activos
            if (hasActiveFilters()) {
                tvEmpty.text = "📭\n\nNo se encontraron transcripciones\ncon los filtros aplicados\n\nIntenta ajustar los filtros"
            } else {
                tvEmpty.text = "📭\n\nNo hay transcripciones guardadas\n\nRealiza una transcripción para verla aquí"
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmpty.visibility = View.GONE
        }

        adapter.updateData(records)
        tvCounter.text = "${records.size} transcripciones"

        // Actualizar visibilidad del botón limpiar
        btnClearFilters.visibility = if (hasActiveFilters()) View.VISIBLE else View.GONE
    }

    private fun hasActiveFilters(): Boolean {
        return currentSearchQuery.isNotEmpty() ||
                currentMode != "Todos" ||
                dateFilterStartMillis != null
    }

    private fun clearAllFilters() {
        etSearch.text.clear()
        spinnerMode.setSelection(0)
        clearDateFilter()
        loadTranscriptions()
    }

    private fun clearDateFilter() {
        dateFilterStartMillis = null
        dateFilterEndMillis = null
        dateFilterType = ""
        btnDateFilter.text = "📅 Filtrar por fecha"
    }

    private fun showDateRangeDialog() {
        val options = arrayOf(
            "Mostrar todos",
            "Hoy",
            "Esta semana",
            "Este mes",
            "Rango personalizado"
        )

        AlertDialog.Builder(this)
            .setTitle("Filtrar por fecha")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> clearDateFilter().also { applyFilters() }
                    1 -> filterByToday()
                    2 -> filterByThisWeek()
                    3 -> filterByThisMonth()
                    4 -> showCustomDateRangePicker()
                }
            }
            .show()
    }

    private fun filterByToday() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfDay = calendar.timeInMillis

        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis

        dateFilterStartMillis = startOfDay
        dateFilterEndMillis = endOfDay
        dateFilterType = "today"

        val records = database.getTodayTranscriptions()
        updateUI(records)
        btnDateFilter.text = "✅ Hoy (${records.size})"
    }

    private fun filterByThisWeek() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfWeek = calendar.timeInMillis

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfWeek = calendar.timeInMillis

        dateFilterStartMillis = startOfWeek
        dateFilterEndMillis = endOfWeek
        dateFilterType = "week"

        val records = database.getThisWeekTranscriptions()
        updateUI(records)
        btnDateFilter.text = "✅ Esta semana (${records.size})"
    }

    private fun filterByThisMonth() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfMonth = calendar.timeInMillis

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfMonth = calendar.timeInMillis

        dateFilterStartMillis = startOfMonth
        dateFilterEndMillis = endOfMonth
        dateFilterType = "month"

        val records = database.getThisMonthTranscriptions()
        updateUI(records)
        btnDateFilter.text = "✅ Este mes (${records.size})"
    }

    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(this, { _, year, month, day ->
            val startCalendar = Calendar.getInstance()
            startCalendar.set(year, month, day, 0, 0, 0)
            startCalendar.set(Calendar.MILLISECOND, 0)
            val startMillis = startCalendar.timeInMillis

            DatePickerDialog(this, { _, year2, month2, day2 ->
                val endCalendar = Calendar.getInstance()
                endCalendar.set(year2, month2, day2, 23, 59, 59)
                endCalendar.set(Calendar.MILLISECOND, 999)
                val endMillis = endCalendar.timeInMillis

                if (endMillis < startMillis) {
                    Toast.makeText(this, "La fecha de fin debe ser posterior a la fecha de inicio", Toast.LENGTH_SHORT).show()
                    return@DatePickerDialog
                }

                dateFilterStartMillis = startMillis
                dateFilterEndMillis = endMillis
                dateFilterType = "custom"

                val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                val startStr = dateFormat.format(Date(startMillis))
                val endStr = dateFormat.format(Date(endMillis))

                applyFilters()
                val filteredRecords = allRecords.filter { record ->
                    record.timestamp in startMillis..endMillis
                }
                btnDateFilter.text = "✅ $startStr - $endStr (${filteredRecords.size})"

            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showStatisticsDialog() {
        val stats = database.getStatistics()

        val message = StringBuilder()
        message.append("📊 ESTADÍSTICAS GENERALES\n\n")
        message.append("Total de transcripciones: ${stats.totalCount}\n\n")

        message.append("🌐 Por Modo:\n")
        message.append("  • Online: ${stats.onlineCount} (${stats.getOnlinePercentage()}%)\n")
        message.append("  • Offline: ${stats.offlineCount} (${stats.getOfflinePercentage()}%)\n\n")

        message.append("📝 Palabras:\n")
        message.append("  • Total: ${stats.totalWords}\n")
        message.append("  • Promedio: ${stats.averageWordsPerTranscription} palabras/transcripción\n\n")

        message.append("⏱️ Duración:\n")
        message.append("  • Total: ${stats.getFormattedTotalDuration()}\n")
        message.append("  • Promedio: ${stats.getFormattedAverageDuration()}\n\n")

        if (stats.oldestTimestamp > 0 && stats.newestTimestamp > 0) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            message.append("📅 Fechas:\n")
            message.append("  • Primera: ${dateFormat.format(Date(stats.oldestTimestamp))}\n")
            message.append("  • Última: ${dateFormat.format(Date(stats.newestTimestamp))}")
        }

        AlertDialog.Builder(this)
            .setTitle("Estadísticas")
            .setMessage(message.toString())
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun showDetailDialog(record: TranscriptionRecord) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        val date = dateFormat.format(Date(record.timestamp))
        val mode = if (record.mode == "online") "🌐 Online" else "📱 Offline"
        val duration = formatDuration(record.durationSeconds)

        val message = """
            📅 Fecha: $date
            🌐 Modo: $mode
            ⏱️ Duración: $duration
            📝 Palabras: ${record.wordCount}
            
            Transcripción:
            ${record.text}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("Detalle de Transcripción")
            .setMessage(message)
            .setPositiveButton("Copiar") { _, _ ->
                copyToClipboard(record.text)
            }
            .setNegativeButton("Eliminar") { _, _ ->
                confirmDelete(record)
            }
            .setNeutralButton("Cerrar", null)
            .show()
    }

    private fun confirmDelete(record: TranscriptionRecord) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Estás seguro de que quieres eliminar esta transcripción?")
            .setPositiveButton("Eliminar") { _, _ ->
                database.deleteTranscription(record.id)
                loadTranscriptions()
                Toast.makeText(this, "Transcripción eliminada", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcripción", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Texto copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            minutes > 0 -> String.format("%d:%02d", minutes, secs)
            else -> String.format("0:%02d", secs)
        }
    }

    // Adapter para RecyclerView adaptado a TU layout item_history.xml
    inner class HistoryAdapter(
        private var records: List<TranscriptionRecord>,
        private val onItemClick: (TranscriptionRecord) -> Unit,
        private val onDeleteClick: (TranscriptionRecord) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvPreview: TextView = view.findViewById(R.id.tvPreview)
            val tvMetadata: TextView = view.findViewById(R.id.tvMetadata)
            val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val record = records[position]
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            // Fecha
            holder.tvDate.text = dateFormat.format(Date(record.timestamp))

            // Preview del texto (máximo 100 caracteres) - USA "text" en lugar de "transcription"
            holder.tvPreview.text = if (record.text.length > 100) {
                record.text.substring(0, 100) + "..."
            } else {
                record.text
            }

            // Metadata: duración + modo + palabras - USA "mode" que es String
            val mode = if (record.mode == "online") "Online" else "Offline"
            val duration = formatDuration(record.durationSeconds)
            holder.tvMetadata.text = "$duration • $mode • ${record.wordCount} palabras"

            // Click en el item completo
            holder.itemView.setOnClickListener {
                onItemClick(record)
            }

            // Click en botón eliminar
            holder.btnDelete.setOnClickListener {
                onDeleteClick(record)
            }
        }

        override fun getItemCount() = records.size

        fun updateData(newRecords: List<TranscriptionRecord>) {
            records = newRecords
            notifyDataSetChanged()
        }
    }
}