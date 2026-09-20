package com.ulsan.disasteralert.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.ulsan.disasteralert.data.CoincidenceSeverity
import com.ulsan.disasteralert.data.PreparednessLevel
import com.ulsan.disasteralert.data.TyphoonAnalyzer
import com.ulsan.disasteralert.data.TyphoonRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ulsan.disasteralert.databinding.ActivityMainBinding
import com.ulsan.disasteralert.util.RegionPrefs
import com.ulsan.disasteralert.worker.WorkScheduler

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: RegionRiskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        requestNotificationPermissionIfNeeded()

        adapter = RegionRiskAdapter { region ->
            val intent = Intent(this, AlertDetailActivity::class.java)
            intent.putExtra(AlertDetailActivity.EXTRA_REGION_NAME, region)
            startActivity(intent)
        }
        binding.recyclerRegions.layoutManager = LinearLayoutManager(this)
        binding.recyclerRegions.adapter = adapter

        binding.fabAddRegion.setOnClickListener {
            startActivity(Intent(this, RegionSettingsActivity::class.java))
        }

        binding.buttonDiagnostics.setOnClickListener {
            startActivity(Intent(this, ApiDiagnosticsActivity::class.java))
        }

        binding.buttonSites.setOnClickListener {
            startActivity(Intent(this, ControlSitesActivity::class.java))
        }

        binding.buttonEvents.setOnClickListener {
            startActivity(Intent(this, EventsActivity::class.java))
        }

        binding.buttonCalibration.setOnClickListener {
            startActivity(Intent(this, CalibrationActivity::class.java))
        }

        binding.bannerTyphoon.setOnClickListener {
            startActivity(Intent(this, TyphoonActivity::class.java))
        }

        loadTyphoonBanner()

        refreshRegionList()
        // 앱 실행 시점에 등록된 모든 지역의 폴링 워커가 살아있는지 재확인
        RegionPrefs.getRegisteredRegions(this).forEach {
            WorkScheduler.scheduleRegion(this, it.name, it.nx, it.ny, it.district)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshRegionList()
    }

    /**
     * 태풍 대비 단계를 상단 배너로 표시한다.
     *
     * 위험도 리스트와 분리된 영역에 둔다 — 시간 척도가 다르기 때문이다.
     * 대비 단계가 PREPARE 이상일 때만 노출해서, 평상시에 화면을 차지하지 않게 한다.
     */
    private fun loadTyphoonBanner() {
        if (com.ulsan.disasteralert.BuildConfig.KMA_APIHUB_KEY.isBlank()) return

        lifecycleScope.launch {
            val top = withContext(Dispatchers.IO) {
                runCatching {
                    val list = TyphoonRepository.getApproaches(
                        this@MainActivity,
                        com.ulsan.disasteralert.BuildConfig.KMA_APIHUB_KEY,
                        com.ulsan.disasteralert.BuildConfig.TIDE_SERVICE_KEY
                    )
                    TyphoonAnalyzer.mostThreatening(list)
                }.getOrNull()
            } ?: return@launch

            if (top.preparednessLevel.ordinal < PreparednessLevel.PREPARE.ordinal) return@launch

            binding.bannerTyphoon.apply {
                visibility = View.VISIBLE
                setBackgroundColor(android.graphics.Color.parseColor(top.preparednessLevel.colorHex))
                text = buildString {
                    append(top.headline)
                    top.tideCoincidence?.let { t ->
                        if (t.severity == CoincidenceSeverity.CRITICAL ||
                            t.severity == CoincidenceSeverity.OVERLAP) {
                            append("\n만조 겹침 — ")
                            append(t.severity.displayName)
                        }
                    }
                    append("\n탭하면 상세 브리핑")
                }
            }
        }
    }

    private fun refreshRegionList() {
        val regions = RegionPrefs.getRegisteredRegions(this).map { it.name }
        adapter.submitList(regions)
        binding.emptyState.visibility =
            if (regions.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001
                )
            }
        }
    }
}
