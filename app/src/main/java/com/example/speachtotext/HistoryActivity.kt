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

    private lateinit var btnBack: ImageButton
    private lateinit var tvTranscriptionCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView

    // NUEVOS: Elementos de búsqueda y filtros
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageButton
    private lateinit var spinnerMode: Spinner
    private lateinit var btnDateFilter: Button
    private lateinit var btnClearFilters: Button
    private lateinit var btnShowStats: Button

    private var btnBottomHome: View? = null
    private var btnBottomHistory: View? = null
    private var btnBottomSettings: View? = null

    private lateinit var database: TranscriptionDatabase
    private lateinit var adapter: HistoryAdapter
    private var allRecords = listOf<TranscriptionRecord>()

    // Variables para filtros
    private var currentSearchKeyword: String? = null
    private var currentMode: String? = null
    private var currentStartDate: Long? = null
    private var currentEndDate: Long? = null

    companion object {
        private const val TAG = "HistoryActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        database = TranscriptionDatabase(this)

        initViews()
        setupRecyclerView()
        setupSearchAndFilters()
        setupListeners()
        loadHistory()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        tvTranscriptionCount = findViewById(R.id.tvTranscriptionCount)
        recyclerView = findViewById(R.id.recyclerViewHistory)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        // NUEVOS: Inicializar elementos de búsqueda y filtros
        etSearch = findViewById(R.id.etSearch)
        btnClearSearch = findViewById(R.id.btnClearSearch)
        spinnerMode = findViewById(R.id.spinnerMode)
        btnDateFilter = findViewById(R.id.btnDateFilter)
        btnClearFilters = findViewById(R.id.btnClearFilters)
        btnShowStats = findViewById(R.id.btnShowStats)

        btnBottomHome = findViewById(R.id.btnBottomHome)
        btnBottomHistory = findViewById(R.id.btnBottomHistory)
        btnBottomSettings = findViewById(R.id.btnBottomSettings)
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { record -> showDetailDialog(record) },
            onDeleteClick = { record -> deleteRecord(record) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearchAndFilters() {
        // Configurar búsqueda en tiempo real
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentSearchKeyword = s?.toString()?.trim()
                btnClearSearch.visibility = if (currentSearchKeyword.isNullOrBlank()) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
                applyFilters()
            }
        })

        // Configurar spinner de modo
        val modeOptions = arrayOf("Todos", "Online", "Offline")
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modeOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMode.adapter = spinnerAdapter

        spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentMode = when (position) {
                    1 -> "online"
                    2 -> "offline"
                    else -> null
                }
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Botón limpiar búsqueda
        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
        }

        // Botón filtro de fecha
        btnDateFilter.setOnClickListener {
            showDateRangeDialog()
        }

        // Botón limpiar todos los filtros
        btnClearFilters.setOnClickListener {
            clearAllFilters()
        }

        // Botón mostrar estadísticas
        btnShowStats.setOnClickListener {
            showStatisticsDialog()
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnBottomHome?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        btnBottomSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }

    private fun loadHistory() {
        allRecords = database.getAllTranscriptions()
        Log.d(TAG, "Transcripciones cargadas: ${allRecords.size}")

        adapter.submitList(allRecords)
        updateEmptyState()
    }

    private fun applyFilters() {
        val filteredRecords = database.searchTranscriptionsAdvanced(
            keyword = if (currentSearchKeyword.isNullOrBlank()) null else currentSearchKeyword,
            mode = currentMode,
            startDate = currentStartDate,
            endDate = currentEndDate
        )

        adapter.submitList(filteredRecords)
        updateEmptyState()

        Log.d(TAG, "Filtros aplicados: keyword='$currentSearchKeyword', mode='$currentMode', resultados=${filteredRecords.size}")
    }

    private fun clearAllFilters() {
        etSearch.text.clear()
        spinnerMode.setSelection(0)
        currentSearchKeyword = null
        currentMode = null
        currentStartDate = null
        currentEndDate = null
        btnDateFilter.text = "📅 Filtrar por fecha"

        loadHistory()
        Toast.makeText(this, "Filtros limpiados", Toast.LENGTH_SHORT).show()
    }

    private fun showDateRangeDialog() {
        val options = arrayOf("Hoy", "Esta semana", "Este mes", "Rango personalizado")

        AlertDialog.Builder(this)
            .setTitle("Filtrar por fecha")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> filterByToday()
                    1 -> filterByThisWeek()
                    2 -> filterByThisMonth()
                    3 -> showCustomDateRangePicker()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun filterByToday() {
        val records = database.getTodayTranscriptions()
        adapter.submitList(records)
        updateEmptyState()
        btnDateFilter.text = "📅 Hoy (${records.size})"
        Toast.makeText(this, "Mostrando: Hoy", Toast.LENGTH_SHORT).show()
    }

    private fun filterByThisWeek() {
        val records = database.getThisWeekTranscriptions()
        adapter.submitList(records)
        updateEmptyState()
        btnDateFilter.text = "📅 Esta semana (${records.size})"
        Toast.makeText(this, "Mostrando: Esta semana", Toast.LENGTH_SHORT).show()
    }

    private fun filterByThisMonth() {
        val records = database.getThisMonthTranscriptions()
        adapter.submitList(records)
        updateEmptyState()
        btnDateFilter.text = "📅 Este mes (${records.size})"
        Toast.makeText(this, "Mostrando: Este mes", Toast.LENGTH_SHORT).show()
    }

    private fun showCustomDateRangePicker() {
        val calendar = Calendar.getInstance()

        // Selector de fecha inicial
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val startCal = Calendar.getInstance()
                startCal.set(year, month, dayOfMonth, 0, 0, 0)
                startCal.set(Calendar.MILLISECOND, 0)
                currentStartDate = startCal.timeInMillis

                // Selector de fecha final
                DatePickerDialog(
                    this,
                    { _, endYear, endMonth, endDayOfMonth ->
                        val endCal = Calendar.getInstance()
                        endCal.set(endYear, endMonth, endDayOfMonth, 23, 59, 59)
                        endCal.set(Calendar.MILLISECOND, 999)
                        currentEndDate = endCal.timeInMillis

                        applyFilters()

                        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
                        btnDateFilter.text = "📅 ${sdf.format(Date(currentStartDate!!))} - ${sdf.format(Date(currentEndDate!!))}"
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showStatisticsDialog() {
        val stats = database.getStatistics()

        val message = buildString {
            appendLine("📊 ESTADÍSTICAS GENERALES")
            appendLine()
            appendLine("Total de transcripciones: ${stats.totalCount}")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("🌐 Online: ${stats.onlineCount} (${stats.getOnlinePercentage()}%)")
            appendLine("📡 Offline: ${stats.offlineCount} (${stats.getOfflinePercentage()}%)")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📝 Total de palabras: ${stats.totalWords}")
            appendLine("📊 Promedio por transcripción: ${stats.averageWordsPerTranscription}")
            appendLine()
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("⏱️ Tiempo total: ${stats.getFormattedTotalDuration()}")
            appendLine("⏱️ Promedio: ${stats.getFormattedAverageDuration()}")

            if (stats.totalCount > 0) {
                appendLine()
                appendLine("━━━━━━━━━━━━━━━━━━━━")
                appendLine()
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                appendLine("📅 Primera: ${sdf.format(Date(stats.oldestTimestamp))}")
                appendLine("📅 Última: ${sdf.format(Date(stats.newestTimestamp))}")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("📊 Estadísticas")
            .setMessage(message)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun updateEmptyState() {
        val count = adapter.itemCount

        // Actualizar contador en el header
        tvTranscriptionCount.text = if (count == 1) {
            "$count transcripción"
        } else {
            "$count transcripciones"
        }

        // Mostrar/ocultar empty state
        if (count == 0) {
            val hasFilters = !currentSearchKeyword.isNullOrBlank() ||
                    currentMode != null ||
                    currentStartDate != null

            tvEmptyState.text = if (hasFilters) {
                "🔍\n\nNo se encontraron resultados\n\nIntenta ajustar los filtros"
            } else {
                "📭\n\nNo hay transcripciones guardadas\n\nRealiza una transcripción para verla aquí"
            }

            tvEmptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            tvEmptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showDetailDialog(record: TranscriptionRecord) {
        AlertDialog.Builder(this)
            .setTitle("📝 Detalle de Transcripción")
            .setMessage("""
                📅 Fecha: ${record.getFormattedDate()}
                ⏱️ Duración: ${record.getFormattedDuration()}
                📊 Palabras: ${record.wordCount}
                🌐 Modo: ${record.mode.uppercase()}
                ${if (record.language != null) "🗣️ Idioma: ${record.language}" else ""}
                
                📄 Texto completo:
                ${record.text}
            """.trimIndent())
            .setPositiveButton("Copiar") { _, _ -> copyToClipboard(record.text) }
            .setNegativeButton("Cerrar", null)
            .setNeutralButton("Eliminar") { _, _ -> deleteRecord(record) }
            .show()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Transcripción", text)
        clipboard.setPrimaryClip(clip)
        NotificationHelper.show(this, "✓ Copiado al portapapeles")
    }

    private fun deleteRecord(record: TranscriptionRecord) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Eliminar transcripción")
            .setMessage("¿Estás seguro de que deseas eliminar esta transcripción?")
            .setPositiveButton("Eliminar") { _, _ ->
                database.deleteTranscription(record.id)
                Log.d(TAG, "Transcripción eliminada - ID: ${record.id}")
                NotificationHelper.show(this, "✓ Transcripción eliminada")

                // Recargar con filtros aplicados
                if (currentSearchKeyword != null || currentMode != null || currentStartDate != null) {
                    applyFilters()
                } else {
                    loadHistory()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/**
 * Adapter para mostrar el historial de transcripciones
 */
class HistoryAdapter(
    private val onItemClick: (TranscriptionRecord) -> Unit,
    private val onDeleteClick: (TranscriptionRecord) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private var records = listOf<TranscriptionRecord>()

    fun submitList(newRecords: List<TranscriptionRecord>) {
        records = newRecords
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    override fun getItemCount() = records.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        private val tvPreview: TextView = itemView.findViewById(R.id.tvPreview)
        private val tvMetadata: TextView = itemView.findViewById(R.id.tvMetadata)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(record: TranscriptionRecord) {
            tvDate.text = record.getFormattedDate()
            tvPreview.text = record.text
            tvMetadata.text = "${record.getFormattedDuration()} • ${record.wordCount} palabras • ${record.mode.uppercase()}"

            itemView.setOnClickListener { onItemClick(record) }
            btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }
}