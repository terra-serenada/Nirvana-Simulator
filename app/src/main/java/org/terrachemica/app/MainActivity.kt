package org.terrachemica.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import org.terrachemica.app.core.ClockMode
import org.terrachemica.app.core.EarthEvolutionModel
import org.terrachemica.app.core.EvolutionClock
import org.terrachemica.app.core.ArchaeanSnapshotFactory
import org.terrachemica.app.core.ArchaeanStoichiometryAudit
import org.terrachemica.app.core.ArchaeanChemicalStateFactory
import org.terrachemica.app.core.AtmosphericRadiativeTransferEngine
import org.terrachemica.app.core.AtmosphericWeatherEngine
import org.terrachemica.app.core.AtmosphericWeatherState
import org.terrachemica.app.core.WeatherForcing
import org.terrachemica.app.core.SolarRadiationEngine
import org.terrachemica.app.core.ReactionNetworkCatalog
import org.terrachemica.app.data.SolarSpectrumRepository
import org.terrachemica.app.view.EarthGlobeView
import java.util.Locale

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: EvolutionClock
    private lateinit var globe: EarthGlobeView
    private lateinit var timeText: TextView
    private lateinit var eonText: TextView
    private lateinit var sunText: TextView
    private lateinit var spectrumText: TextView
    private lateinit var radiationText: TextView
    private lateinit var atmosphereText: TextView
    private lateinit var rotationText: TextView
    private lateinit var referenceLedgerText: TextView
    private lateinit var snapshotApplicabilityText: TextView
    private lateinit var reactionNetworkText: TextView
    private lateinit var checkpointText: TextView
    private lateinit var weatherText: TextView
    private lateinit var modeButton: Button
    private lateinit var weatherButton: Button
    private lateinit var playButton: Button
    private lateinit var progress: ProgressBar
    private var lastPersistNanos = 0L
    private var spectrumStatus = "Menyiapkan referensi spektrum Matahari…"
    private var lastRadiationRequestKey: String? = null
    private var weatherEnabled = true
    private val weatherEngine = AtmosphericWeatherEngine()
    private var weatherForcing: WeatherForcing? = null
    private var lastWeatherTickNanos = 0L
    private val weatherPreset by lazy {
        AtmosphericRadiativeTransferEngine.buildModernEffectivePreset(
            SolarRadiationEngine.bands.map { it.id }.toSet()
        )
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!::clock.isInitialized) return
            val now = SystemClock.elapsedRealtimeNanos()
            val realDeltaSeconds = if (lastWeatherTickNanos == 0L) 0.0
                else (now - lastWeatherTickNanos).coerceAtLeast(0L) / 1_000_000_000.0
            lastWeatherTickNanos = now
            val state = clock.advance(now)
            render(state.ageSinceFormationMa)
            val forcing = weatherForcing
            if (forcing != null && realDeltaSeconds > 0.0) {
                val weatherState = weatherEngine.advance(realDeltaSeconds * 3600.0, forcing)
                globe.setWeatherState(weatherState)
                updateWeatherText(weatherState)
            } else if (forcing == null) {
                globe.setWeatherState(null)
            }
            if (now - lastPersistNanos > 5_000_000_000L) {
                persistState()
                lastPersistNanos = now
            }
            handler.postDelayed(this, 250L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = Color.rgb(5, 13, 25)
        window.navigationBarColor = Color.rgb(5, 13, 25)

        val prefs = getSharedPreferences("terra_chemica_state", MODE_PRIVATE)
        // v0.3 persists the Double as text. A Float loses sub-million-year
        // precision near the end of the 4.54 Ga timeline.
        val savedAge = prefs.getString("age_ma_exact", null)?.toDoubleOrNull()
            ?: prefs.getFloat("age_ma", 0f).toDouble() // one-time migration from v0.1
        val savedMode = runCatching { ClockMode.valueOf(prefs.getString("mode", ClockMode.AUTO_JOURNEY.name)!!) }
            .getOrDefault(ClockMode.AUTO_JOURNEY)
        clock = EvolutionClock(
            initialAgeSinceFormationMa = savedAge,
            initialMode = savedMode,
            initialPlaying = savedAge < EvolutionClock.EARTH_AGE_MA
        )

        buildInterface()
        render(clock.ageSinceFormationMa)
        SolarSpectrumRepository.downloadMissing(this) { status ->
            runOnUiThread {
                spectrumStatus = status
                if (!isFinishing && ::spectrumText.isInitialized) {
                    spectrumText.text = status
                    if (status.startsWith("Spektrum tervalidasi:")) {
                        lastRadiationRequestKey = null
                        requestRadiation(EarthEvolutionModel.snapshot(clock.ageSinceFormationMa).lookbackMa / 1000.0, force = true)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lastWeatherTickNanos = 0L
        if (::clock.isInitialized) clock.setPlaying(clock.isPlaying)
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onPause() {
        persistState()
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(12))
            setBackgroundColor(Color.rgb(5, 13, 25))
        }

        val title = text("TERRA CHEMICA", 20f, Color.WHITE, true).apply { letterSpacing = 0.13f }
        root.addView(title, matchWrap())
        val subtitle = text("EARTH EVOLUTION  ·  LOCAL SIMULATION", 10f, Color.rgb(145, 185, 215), true)
        subtitle.setPadding(0, 0, 0, dp(5))
        root.addView(subtitle, matchWrap())

        globe = EarthGlobeView(this)
        root.addView(globe, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(3)
            bottomMargin = dp(7)
        })

        timeText = text("Umur simulasi: —", 18f, Color.WHITE, true)
        root.addView(timeText, matchWrap())
        eonText = text("Eon: —", 14f, Color.rgb(120, 205, 235), true)
        root.addView(eonText, matchWrap())
        checkpointText = text("Titik perjalanan: —", 12f, Color.rgb(220, 230, 238), false)
        checkpointText.maxLines = 2
        checkpointText.setPadding(0, dp(3), 0, dp(5))
        root.addView(checkpointText, matchWrap())
        weatherText = text(
            "CUACA: menunggu forcing radiasi yang didukung model…",
            11f, Color.rgb(190, 220, 235), true
        ).apply { maxLines = 4; setPadding(dp(9), dp(7), dp(9), dp(7)); setBackgroundColor(Color.rgb(16, 39, 55)) }
        root.addView(weatherText, matchWrap())

        val solarBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(13, 29, 46))
        }
        sunText = text("Matahari: —", 12f, Color.WHITE, true)
        spectrumText = text(spectrumStatus, 10f, Color.rgb(155, 180, 200), false).apply { maxLines = 2 }
        radiationText = text("Radiasi Matahari: menunggu spektrum tervalidasi…", 10f, Color.rgb(205, 220, 230), false).apply { maxLines = 4 }
        atmosphereText = text("Transfer atmosfer: menunggu spektrum tervalidasi…", 10f, Color.rgb(205, 220, 230), false).apply { maxLines = 7 }
        rotationText = text("Rotasi Bumi: —", 10f, Color.rgb(155, 180, 200), false).apply { maxLines = 2 }
        val ledgerReport = runCatching { ArchaeanSnapshotFactory.validate() }.getOrNull()
        val stoichReport = runCatching { ArchaeanStoichiometryAudit.validate() }.getOrNull()
        referenceLedgerText = text(
            if (ledgerReport != null && stoichReport?.valid == true) {
                "Audit Archaean: ${stoichReport.speciesCount} spesies + ${stoichReport.waterStoreCount} store H₂O cocok dengan ${stoichReport.comparisonCount} nilai unsur · residual maks ${String.format(Locale.US, "%.2e", stoichReport.maxRelativeDifference)}"
            } else {
                "Audit inventaris referensi Archaean belum lolos"
            },
            9f, Color.rgb(155, 180, 200), false
        ).apply { maxLines = 3 }
        snapshotApplicabilityText = text("Snapshot 3,8 Ga: —", 9f, Color.rgb(155, 180, 200), false).apply { maxLines = 2 }
        val chemicalStateReport = runCatching { ArchaeanChemicalStateFactory.validate() }.getOrNull()
        val reactionReport = runCatching {
            ReactionNetworkCatalog.validateAll(ArchaeanChemicalStateFactory.create())
        }.getOrNull()
        reactionNetworkText = text(
            if (chemicalStateReport != null && chemicalStateReport.valid && reactionReport != null && reactionReport.valid) {
                "Jaringan reaksi: ${reactionReport.balancedCount}/${reactionReport.reactionCount} seimbang · ${reactionReport.withStandardEnthalpyCount} berentalpi standar · ${reactionReport.withKineticLawCount} laju terkalibrasi; tidak berjalan otomatis"
            } else {
                "Audit jaringan reaksi belum lolos"
            },
            9f, Color.rgb(155, 180, 200), false
        ).apply { maxLines = 3 }
        solarBox.addView(sunText, matchWrap())
        solarBox.addView(spectrumText, matchWrap())
        solarBox.addView(radiationText, matchWrap())
        solarBox.addView(atmosphereText, matchWrap())
        solarBox.addView(rotationText, matchWrap())
        solarBox.addView(referenceLedgerText, matchWrap())
        solarBox.addView(snapshotApplicabilityText, matchWrap())
        solarBox.addView(reactionNetworkText, matchWrap())
        root.addView(solarBox, matchWrap())

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 10_000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(63, 175, 205))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(35, 55, 74))
        }
        root.addView(progress, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply {
            topMargin = dp(10)
            bottomMargin = dp(8)
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        playButton = Button(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(27, 105, 132)))
            setOnClickListener {
                clock.togglePlaying()
                updateButtons()
            }
        }
        modeButton = Button(this).apply {
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(45, 72, 98)))
            setOnClickListener {
                clock.toggleMode()
                updateButtons()
            }
        }
        weatherButton = Button(this).apply {
            textSize = 11f
            setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(68, 86, 105)))
            setOnClickListener {
                weatherEnabled = !weatherEnabled
                globe.setWeatherEnabled(weatherEnabled)
                weatherButton.text = if (weatherEnabled) "CUACA ON" else "CUACA OFF"
            }
        }
        controls.addView(playButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(3) })
        controls.addView(modeButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        controls.addView(weatherButton, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3) })
        root.addView(controls, matchWrap())

        val footnote = text("Prototipe v0.7 · cuaca kolom sederhana terhubung ke suhu radiasi; bukan prakiraan atau rekonstruksi paleoklimat tervalidasi", 9f, Color.rgb(120, 145, 165), false)
        footnote.setPadding(0, dp(7), 0, 0)
        root.addView(footnote, matchWrap())
        setContentView(root)
        updateButtons()
    }

    private fun render(ageMa: Double) {
        val snapshot = EarthEvolutionModel.snapshot(ageMa)
        globe.setSnapshot(snapshot)
        val luminosityForWeather = snapshot.solarLuminosityFraction
        if (luminosityForWeather != null) {
            val greyForWeather = AtmosphericRadiativeTransferEngine.calculateGreyLongwaveEquilibrium(
                solarAgeGa = snapshot.sunAgeGa,
                luminosityFraction = luminosityForWeather,
                preset = weatherPreset
            )
            weatherForcing = WeatherForcing(
                solarLuminosityFraction = luminosityForWeather,
                equilibriumSurfaceTemperatureK = greyForWeather.surfaceTemperatureK
            )
            val currentWeather = weatherEngine.currentState()
            if (currentWeather == null) {
                weatherText.text = "CUACA: membentuk kolom uap air dari forcing Matahari + suhu radiasi…\nJam cuaca dipercepat: 1 detik nyata = 1 jam cuaca"
            }
        } else {
            if (weatherForcing != null) weatherEngine.reset()
            weatherForcing = null
            weatherText.text = "CUACA BELUM TERHITUNG: fase Matahari sangat muda berada di luar validitas model luminositas yang tersedia.\nTidak ada hujan/petir yang direka untuk interval ini."
        }

        val earthAgeGa = ageMa / 1000.0
        timeText.text = String.format(Locale.US, "Umur Bumi: %.3f Ga", earthAgeGa)
        eonText.text = "${snapshot.eon.displayName}  ·  ${formatLookback(snapshot.lookbackMa)} sebelum sekarang"
        val milestone = EvolutionClock.milestones.lastOrNull { it.ageSinceFormationMa <= ageMa + 1e-7 }
        checkpointText.text = milestone?.let { "${it.title}\n${it.description}" } ?: "Perjalanan dimulai"

        val luminosity = snapshot.solarLuminosityFraction?.let { String.format(Locale.US, "%.3f × luminositas kini", it) }
            ?: "belum dikalibrasi untuk fase Matahari sangat muda"
        sunText.text = String.format(Locale.US, "Matahari: umur %.3f Ga  ·  %s\n%s", snapshot.sunAgeGa, luminosity, snapshot.solarModelStatus)
        val anchor = snapshot.nearestSpectrum
        val cacheCount = SolarSpectrumRepository.cachedCount(this)
        spectrumText.text = "Spektrum terdekat: ${anchor.fileName} (${anchor.label})\nCache spektrum: $cacheCount/${SolarSpectrumRepository.remoteFiles.size} · $spectrumStatus"
        rotationText.text = snapshot.dayLengthHours?.let {
            String.format(Locale.US, "Panjang hari acuan: %.3f jam · %s", it, snapshot.dayLengthStatus)
        } ?: snapshot.dayLengthStatus
        requestRadiation(snapshot.lookbackMa / 1000.0)
        snapshotApplicabilityText.text = if (kotlin.math.abs(snapshot.lookbackMa - 3800.0) < 0.5) {
            "Snapshot inventaris Archaean aktif pada jangkar 3,8 Ga"
        } else {
            "Snapshot inventaris 3,8 Ga tidak diekstrapolasi ke era ini"
        }
        progress.progress = ((ageMa / EvolutionClock.EARTH_AGE_MA) * progress.max).toInt().coerceIn(0, progress.max)
        updateButtons()
    }


    private fun requestRadiation(lookbackGa: Double, force: Boolean = false) {
        if (!::radiationText.isInitialized) return
        // 25 Ma display buckets avoid repeatedly parsing/integrating ~26k spectral points
        // during the 250 ms UI refresh. Scientific Daily remains exact; this is only a
        // refresh cadence for the slowly changing display diagnostic.
        val exactAnchor = EarthEvolutionModel.spectrumAnchors.firstOrNull {
            kotlin.math.abs(it.lookbackGa - lookbackGa) < 1e-8
        }
        val requestKey = exactAnchor?.let { "anchor:${it.fileName}" }
            ?: "bucket:${kotlin.math.round(lookbackGa * 40.0).toLong()}"
        if (!force && requestKey == lastRadiationRequestKey) return
        lastRadiationRequestKey = requestKey
        radiationText.text = "Menghitung spektrum pada ${String.format(Locale.US, "%.3f", lookbackGa)} Ga lalu…"
        SolarSpectrumRepository.calculateAtLookbackAsync(this, lookbackGa) { result ->
            runOnUiThread {
                if (isFinishing || !::radiationText.isInitialized || lastRadiationRequestKey != requestKey) return@runOnUiThread
                when (result) {
                    is SolarSpectrumRepository.LookupResult.Unavailable -> {
                        radiationText.text = "Radiasi Matahari belum tersedia: ${result.reason}"
                        atmosphereText.text = "Transfer atmosfer belum dihitung karena spektrum untuk epoch ini tidak tersedia. Tidak ada ekstrapolasi atau penggunaan hasil epoch sebelumnya."
                    }
                    is SolarSpectrumRepository.LookupResult.Available -> {
                        val report = result.report
                        val uv = report.bands.filter { it.band.maximumNm <= 400.0 }.sumOf { it.irradianceWm2 }
                        val visible = report.bands.first { it.band.id == "VISIBLE" }.irradianceWm2
                        val nir = report.bands.first { it.band.id == "NIR" }.irradianceWm2
                        radiationText.text = String.format(
                            Locale.US,
                            "Irradiansi domain 0,5–2397,51 nm: %.6f W/m² pada 1 AU\nRerata geometri global: %.6f W/m² (÷4)\nUV/XUV: %.3f · Visible: %.3f · NIR: %.3f W/m²\n%s · integral spektral parsial, bukan TSI bolometrik",
                            report.coveredBandIrradianceWm2,
                            report.globalMeanIncomingWm2,
                            uv,
                            visible,
                            nir,
                            result.sourceDescription
                        )

                        val effectivePreset = AtmosphericRadiativeTransferEngine.buildModernEffectivePreset(
                            report.bands.map { it.band.id }.toSet()
                        )
                        val shortwaveBudget = AtmosphericRadiativeTransferEngine.calculateShortwave(
                            report,
                            effectivePreset.shortwaveModel
                        )
                        val sunAgeGa = (EarthEvolutionModel.SUN_AGE_GA - lookbackGa).coerceAtLeast(0.0)
                        val luminosity = EarthEvolutionModel.goughLuminosityFraction(sunAgeGa)
                        val shortwaveText = String.format(
                            Locale.US,
                            "Transfer spektrum parsial: atmosfer %.2f · permukaan %.2f · refleksi %.2f W/m²; residual %.1e W/m²",
                            shortwaveBudget.atmosphereAbsorbedWm2,
                            shortwaveBudget.surfaceAbsorbedWm2,
                            shortwaveBudget.reflectedToSpaceWm2,
                            shortwaveBudget.energyResidualWm2
                        )
                        val equilibriumText = if (luminosity != null) {
                            val grey = AtmosphericRadiativeTransferEngine.calculateGreyLongwaveEquilibrium(
                                sunAgeGa,
                                luminosity,
                                effectivePreset
                            )
                            String.format(
                                Locale.US,
                                "TSI model %.1f W/m² · T permukaan efektif %.2f K · T atmosfer efektif %.2f K · OLR %.2f W/m²",
                                grey.totalSolarIrradianceWm2,
                                grey.surfaceTemperatureK,
                                grey.effectiveAtmosphereTemperatureK,
                                grey.outgoingLongwaveToSpaceWm2,
                                grey.topOfAtmosphereEnergyResidualWm2
                            )
                        } else {
                            "Kesetimbangan bolometrik belum dihitung: umur Matahari berada di luar validitas pendekatan luminositas yang tersedia."
                        }
                        atmosphereText.text = "$shortwaveText\n$equilibriumText\nParameter optik modern ditahan tetap: sensitivitas Matahari, bukan rekonstruksi paleoklima. Hamburan, awan, aerosol, konveksi, dan panas laten belum dimodelkan."
                    }
                }
            }
        }
    }

    private fun updateWeatherText(state: AtmosphericWeatherState) {
        val lightning = state.lightningEventCount
        weatherText.text = String.format(
            Locale.US,
            "CUACA: %s  ·  RH %.0f%%  ·  awan %.0f%%\nHujan %.2f mm/jam  ·  kolom uap %.1f mm  ·  air awan %.2f mm\nIndeks konveksi %.2f  ·  kilat terhitung %d  ·  jam cuaca %.1f\nNeraca air per langkah: %.2e mm · 1 detik nyata = 1 jam cuaca",
            state.regime.label,
            state.relativeHumidity * 100.0,
            state.cloudFraction * 100.0,
            state.precipitationRateMmPerHour,
            state.waterVaporColumnMm,
            state.cloudLiquidWaterMm,
            state.convectionIndex,
            lightning,
            state.localHour,
            state.waterBalanceResidualMm
        )
    }

    private fun updateButtons() {
        if (!::playButton.isInitialized || !::modeButton.isInitialized) return
        weatherButton.text = if (weatherEnabled) "CUACA ON" else "CUACA OFF"
        playButton.text = if (clock.isPlaying) "JEDA" else if (clock.ageSinceFormationMa >= EvolutionClock.EARTH_AGE_MA) "SELESAI" else "LANJUTKAN"
        playButton.isEnabled = clock.ageSinceFormationMa < EvolutionClock.EARTH_AGE_MA
        modeButton.text = if (clock.mode == ClockMode.AUTO_JOURNEY) "JELAJAH OTOMATIS" else "1 DETIK = 1 HARI"
    }

    private fun persistState() {
        if (!::clock.isInitialized) return
        getSharedPreferences("terra_chemica_state", MODE_PRIVATE).edit()
            .putString("age_ma_exact", clock.ageSinceFormationMa.toString())
            .remove("age_ma")
            .putString("mode", clock.mode.name)
            .apply()
    }

    private fun formatLookback(ma: Double): String = if (ma >= 1000.0) {
        String.format(Locale.US, "%.3f Ga", ma / 1000.0)
    } else {
        String.format(Locale.US, "%.1f Ma", ma)
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun matchWrap() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
