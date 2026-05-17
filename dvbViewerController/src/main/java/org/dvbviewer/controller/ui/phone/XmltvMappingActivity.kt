package org.dvbviewer.controller.ui.phone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.dvbviewer.controller.R
import org.dvbviewer.controller.data.xmltv.XmltvRepository
import org.dvbviewer.controller.databinding.ActivityXmltvMappingBinding
import org.dvbviewer.controller.databinding.ListItemXmltvMappingBinding

/**
 * Shows and edits the manual DVBViewer → XMLTV channel name mapping.
 *
 * Each entry has priority over both exact-match and fuzzy-match logic in
 * [XmltvRepository.getEpgForChannel]. This is the escape hatch for channels
 * whose names differ too much for fuzzy matching to work automatically.
 */
class XmltvMappingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityXmltvMappingBinding
    private lateinit var repo: XmltvRepository
    private lateinit var adapter: MappingAdapter

    // In-memory copy of the mapping; persisted on every change.
    private val mappingData = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityXmltvMappingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.pref_xmltv_mapping_title)
        }

        repo = XmltvRepository(applicationContext)
        mappingData.putAll(repo.loadChannelMapping())

        adapter = MappingAdapter(onDelete = { dvbName ->
            mappingData.remove(dvbName)
            repo.saveChannelMapping(mappingData)
            refreshUi()
        })

        binding.mappingList.apply {
            layoutManager = LinearLayoutManager(this@XmltvMappingActivity)
            this.adapter  = this@XmltvMappingActivity.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        binding.fabAdd.setOnClickListener { showAddDialog() }

        refreshUi()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { finish(); return true }
        return super.onOptionsItemSelected(item)
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun refreshUi() {
        val sorted = mappingData.entries.sortedBy { it.key }.map { it.key to it.value }
        adapter.setData(sorted)
        binding.emptyView.visibility =
            if (sorted.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        binding.mappingList.visibility =
            if (sorted.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun showAddDialog() {
        val dp16 = (16 * resources.displayMetrics.density).toInt()
        val dp8  = dp16 / 2

        val etDvb = EditText(this).apply {
            hint = getString(R.string.xmltv_mapping_dvb_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val etXmltv = EditText(this).apply {
            hint = getString(R.string.xmltv_mapping_xmltv_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp16, dp8, dp16, 0)
            addView(etDvb)
            addView(etXmltv.also { it.setPadding(0, dp8, 0, 0) })
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.xmltv_mapping_add_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val dvb   = etDvb.text.toString().trim()
                val xmltv = etXmltv.text.toString().trim()
                when {
                    dvb.isEmpty()   -> toast(R.string.xmltv_mapping_error_dvb_empty)
                    xmltv.isEmpty() -> toast(R.string.xmltv_mapping_error_xmltv_empty)
                    else -> {
                        mappingData[dvb] = xmltv
                        repo.saveChannelMapping(mappingData)
                        refreshUi()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toast(resId: Int) =
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()

    // ── RecyclerView adapter ──────────────────────────────────────────────────

    private inner class MappingAdapter(
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<MappingAdapter.VH>() {

        private val items = mutableListOf<Pair<String, String>>()

        fun setData(data: List<Pair<String, String>>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        inner class VH(val b: ListItemXmltvMappingBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ListItemXmltvMappingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (dvbName, xmltvName) = items[position]
            holder.b.tvDvbName.text   = dvbName
            holder.b.tvXmltvName.text = xmltvName
            holder.b.btnDelete.setOnClickListener { onDelete(dvbName) }
        }
    }
}
