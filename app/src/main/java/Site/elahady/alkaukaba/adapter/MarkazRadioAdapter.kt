package site.elahady.alkaukaba.adapter

import site.elahady.alkaukaba.databinding.ItemMarkazRadioBinding
import site.elahady.alkaukaba.model.MarkazNasional
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

// Adapter single-select untuk PilihKotaActivity - beda dari MarkazCheckboxAdapter (multi-select
// checklist di Hisab Nasional): `items[0]` selalu null (baris sentinel "Gunakan GPS/Lokasi
// Otomatis"), sisanya HisabNasionalCalculator.allMarkaz. Tap baris manapun langsung memilih &
// memicu `onPick` - tidak ada tombol "Simpan" terpisah seperti checklist multi-select.
class MarkazRadioAdapter(
    private val items: List<MarkazNasional?>,
    private var selectedId: String?,
    private val onPick: (MarkazNasional?) -> Unit
) : RecyclerView.Adapter<MarkazRadioAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemMarkazRadioBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MarkazNasional?) {
            if (item == null) {
                binding.tvKota.text = "Gunakan GPS / Lokasi Otomatis"
                binding.tvProvinsi.text = "Ikuti lokasi perangkat (GPS/manual/fallback)"
            } else {
                binding.tvKota.text = item.nama
                binding.tvProvinsi.text = item.provinsi
            }
            binding.rbMarkaz.isChecked = item?.id == selectedId

            binding.rowMarkaz.setOnClickListener {
                selectedId = item?.id
                notifyDataSetChanged()
                onPick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMarkazRadioBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
