package com.example.speachtotext

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
    private var btnBottomHome: View? = null
    private var btnBottomHistory: View? = null
    private var btnBottomSettings: View? = null

    private lateinit var database: TranscriptionDatabase
    private lateinit var adapter: HistoryAdapter
    private var allRecords = listOf<TranscriptionRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        database = TranscriptionDatabase(this)

        initViews()
        setupRecyclerView()
        setupListeners()
        loadHistory()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btnBack)
        recyclerView = findViewById(R.id.recyclerViewHistory)
        tvEmptyState = findViewById(R.id.tvEmptyState)
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

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnBottomHome?.setOnClickListener {
            finish()
        }

        btnBottomSettings?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }

    private fun loadHistory() {
        allRecords = database.getAllTranscriptions()
        adapter.submitList(allRecords)
        updateEmptyState()
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
                NotificationHelper.show(this, "✓ Transcripción eliminada")
                loadHistory()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/**
 * Adapter actualizado para el nuevo diseño
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
            tvMetadata.text = record.getFormattedDuration()

            itemView.setOnClickListener { onItemClick(record) }
            btnDelete.setOnClickListener { onDeleteClick(record) }
        }
    }
}