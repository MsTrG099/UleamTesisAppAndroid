package com.example.speachtotext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
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

class HistoryActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var tvStats: TextView
    private lateinit var btnClearAll: Button
    private lateinit var searchView: SearchView
    private lateinit var spinnerFilter: Spinner

    private lateinit var database: TranscriptionDatabase
    private lateinit var adapter: HistoryAdapter
    private var allRecords = listOf<TranscriptionRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        database = TranscriptionDatabase(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        loadHistory()
        updateStatistics()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        recyclerView = findViewById(R.id.recyclerViewHistory)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvStats = findViewById(R.id.tvStats)
        btnClearAll = findViewById(R.id.btnClearAll)
        searchView = findViewById(R.id.searchView)
        spinnerFilter = findViewById(R.id.spinnerFilter)
    }

    private fun setupRecyclerView() {
        adapter = HistoryAdapter(
            onItemClick = { record -> showDetailDialog(record) },
            onCopyClick = { record -> copyToClipboard(record.text) },
            onDeleteClick = { record -> deleteRecord(record) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnClearAll.setOnClickListener {
            showClearAllDialog()
        }

        // Filtro por modo
        val filterOptions = arrayOf("Todos", "Online", "Offline")
        spinnerFilter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, filterOptions)
        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterRecords(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Búsqueda
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchRecords(query ?: "")
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    loadHistory()
                } else {
                    searchRecords(newText)
                }
                return true
            }
        })
    }

    private fun loadHistory() {
        allRecords = database.getAllTranscriptions()
        adapter.submitList(allRecords)
        updateEmptyState()
    }

    private fun filterRecords(filterPosition: Int) {
        val filteredRecords = when (filterPosition) {
            0 -> allRecords // Todos
            1 -> allRecords.filter { it.mode == "online" } // Online
            2 -> allRecords.filter { it.mode == "offline" } // Offline
            else -> allRecords
        }
        adapter.submitList(filteredRecords)
    }

    private fun searchRecords(query: String) {
        val searchResults = database.searchTranscriptions(query)
        adapter.submitList(searchResults)
        updateEmptyState()
    }

    private fun updateStatistics() {
        val stats = database.getStatistics()
        tvStats.text = """
            📊 Estadísticas
            • Total: ${stats.totalCount} transcripciones
            • Palabras: ${stats.totalWords} (promedio: ${stats.avgWords.toInt()})
            • Duración: ${stats.getFormattedTotalDuration()}
            • Online: ${stats.onlineCount} | Offline: ${stats.offlineCount}
        """.trimIndent()
    }

    private fun updateEmptyState() {
        if (adapter.itemCount == 0) {
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
                ${if (record.sampleRate != null) "🎙️ Sample Rate: ${record.sampleRate} Hz" else ""}
                
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
        Toast.makeText(this, "✓ Copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    private fun deleteRecord(record: TranscriptionRecord) {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Eliminar transcripción")
            .setMessage("¿Estás seguro de que deseas eliminar esta transcripción?")
            .setPositiveButton("Eliminar") { _, _ ->
                database.deleteTranscription(record.id)
                Toast.makeText(this, "✓ Transcripción eliminada", Toast.LENGTH_SHORT).show()
                loadHistory()
                updateStatistics()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showClearAllDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Eliminar todo el historial")
            .setMessage("¿Estás seguro? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar todo") { _, _ ->
                val count = database.deleteAllTranscriptions()
                Toast.makeText(this, "✓ $count transcripciones eliminadas", Toast.LENGTH_SHORT).show()
                loadHistory()
                updateStatistics()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/**
 * Adapter para el RecyclerView del historial
 */
class HistoryAdapter(
    private val onItemClick: (TranscriptionRecord) -> Unit,
    private val onCopyClick: (TranscriptionRecord) -> Unit,
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
        private val tvMode: TextView = itemView.findViewById(R.id.tvMode)
        private val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(record: TranscriptionRecord) {
            tvDate.text = record.getFormattedDate()
            tvPreview.text = record.getPreviewText(80)
            tvMetadata.text = "${record.wordCount} palabras • ${record.getFormattedDuration()}"

            // Modo con color
            tvMode.text = record.mode.uppercase()
            tvMode.setBackgroundColor(
                if (record.mode == "online")
                    android.graphics.Color.parseColor("#4CAF50")
                else
                    android.graphics.Color.parseColor("#FF9800")
            )

            itemView.setOnClickListener { onItemClick(record) }
            btnCopy.setOnClickListener { onCopyClick(record) }
            btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }
}