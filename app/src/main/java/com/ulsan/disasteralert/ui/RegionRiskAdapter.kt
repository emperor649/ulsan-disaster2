package com.ulsan.disasteralert.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.ulsan.disasteralert.databinding.ItemRegionRiskBinding

class RegionRiskAdapter(
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<RegionRiskAdapter.ViewHolder>() {

    private var regions: List<String> = emptyList()

    fun submitList(newRegions: List<String>) {
        regions = newRegions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRegionRiskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(regions[position])
    }

    override fun getItemCount() = regions.size

    inner class ViewHolder(private val binding: ItemRegionRiskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(regionName: String) {
            binding.textRegionName.text = regionName
            // 실제 구현에서는 마지막으로 계산된 RegionRiskStatus를 로컬 DB/캐시에서 읽어와
            // 위험도 뱃지 색상과 점수를 표시한다.
            binding.textRiskLevel.text = "확인 중..."
            binding.root.setOnClickListener { onClick(regionName) }
        }
    }
}
