package com.ulsan.disasteralert.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.ulsan.disasteralert.util.districtOf
import com.ulsan.disasteralert.databinding.ActivityAlertDetailBinding

class AlertDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val regionName = intent.getStringExtra(EXTRA_REGION_NAME) ?: return
        binding.textDetailRegionName.text = regionName
        // 과거 이력 분석 화면으로 진입
        binding.buttonHistoryAnalysis.setOnClickListener {
            val intent = android.content.Intent(this, HistoryAnalysisActivity::class.java)
            intent.putExtra(HistoryAnalysisActivity.EXTRA_DISTRICT, districtOf(regionName))
            startActivity(intent)
        }
        binding.buttonRiverLevel.setOnClickListener {
            val intent = android.content.Intent(this, RiverLevelActivity::class.java)
            intent.putExtra(RiverLevelActivity.EXTRA_DISTRICT, districtOf(regionName))
            startActivity(intent)
        }
        binding.buttonTide.setOnClickListener {
            startActivity(android.content.Intent(this, TideActivity::class.java))
        }
        // 실제 구현: 로컬 캐시(Room)에서 최신 RegionRiskStatus를 조회해
        // 특보 목록 / 강수량 추이 그래프 / 재난문자 원문을 표시
    }

    companion object {
        const val EXTRA_REGION_NAME = "extra_region_name"
    }
}
