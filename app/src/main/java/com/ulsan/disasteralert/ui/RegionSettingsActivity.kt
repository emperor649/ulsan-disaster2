package com.ulsan.disasteralert.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.ulsan.disasteralert.databinding.ActivityRegionSettingsBinding
import com.ulsan.disasteralert.util.RegionPrefs
import com.ulsan.disasteralert.util.RegisteredRegion
import com.ulsan.disasteralert.util.UlsanGridPresets
import com.ulsan.disasteralert.worker.WorkScheduler

/**
 * 관심 지역 등록.
 *
 * 격자좌표는 미리 계산된 울산 프리셋에서 선택하게 했다.
 * (기상청 API는 위경도가 아닌 5km 격자를 쓰고, 변환 공식이 단순하지 않아
 *  사용자가 직접 입력하게 하면 실수가 나기 쉽다.)
 */
class RegionSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegionSettingsBinding
    private val presets = UlsanGridPresets.presets

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegionSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            presets.map { "${it.displayName}  (격자 ${it.nx},${it.ny})" }
        )
        binding.spinnerPreset.adapter = adapter

        binding.spinnerPreset.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?, view: android.view.View?,
                    position: Int, id: Long
                ) {
                    val p = presets[position]
                    binding.textPresetDetail.text = buildString {
                        appendLine("관할: ${p.district}")
                        appendLine("격자좌표: nx=${p.nx}, ny=${p.ny}")
                        append("위경도: ${p.latitude}, ${p.longitude}")
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }

        binding.buttonSave.setOnClickListener {
            val p = presets[binding.spinnerPreset.selectedItemPosition]
            RegionPrefs.addRegion(this, RegisteredRegion(p.displayName, p.nx, p.ny, p.district))
            WorkScheduler.scheduleRegion(this, p.displayName, p.nx, p.ny, p.district)
            finish()
        }
    }
}
