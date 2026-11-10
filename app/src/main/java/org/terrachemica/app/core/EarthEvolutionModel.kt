package org.terrachemica.app.core

import kotlin.math.abs

/** Scientific constants and reference epochs used by the first prototype. */
object EarthEvolutionModel {
    const val EARTH_AGE_GA = 4.540
    const val SUN_AGE_GA = 4.568
    const val ARCHAEAN_LOOKBACK_GA = 3.800
    const val ARCHAEAN_ROTATION_HOURS = 14.0
    const val MODERN_SIDEREAL_DAY_HOURS = 23.9340905

    enum class Eon(val displayName: String) {
        HADEAN("Hadean"),
        ARCHAEAN("Archaean"),
        PROTEROZOIC("Proterozoic"),
        PHANEROZOIC("Phanerozoic")
    }

    data class SpectrumAnchor(
        val lookbackGa: Double,
        val fileName: String,
        val label: String
    )

    data class Snapshot(
        val ageSinceFormationMa: Double,
        val lookbackMa: Double,
        val eon: Eon,
        val sunAgeGa: Double,
        val solarLuminosityFraction: Double?,
        val solarModelStatus: String,
        val nearestSpectrum: SpectrumAnchor,
        val dayLengthHours: Double?,
        val dayLengthStatus: String
    )

    val spectrumAnchors = listOf(
        SpectrumAnchor(4.4, "Sun4.4Ga.txt", "4,4 Ga · VPL / Claire et al. (2012)"),
        SpectrumAnchor(3.8, "Sun3.8Ga.txt", "3,8 Ga · VPL / Claire et al. (2012)"),
        SpectrumAnchor(2.7, "Sun2.7Ga.txt", "2,7 Ga · VPL / Claire et al. (2012)"),
        SpectrumAnchor(2.4, "Sun2.4Ga.txt", "2,4 Ga · VPL / Claire et al. (2012)"),
        SpectrumAnchor(1.8, "Sun1.8Ga.txt", "1,8 Ga · VPL / Claire et al. (2012)"),
        SpectrumAnchor(0.6, "Sun0.6Ga.txt", "0,6 Ga · VPL / Claire et al. (2012)"),
        SpectrumAnchor(0.0, "SunModern.txt", "Modern · VPL / Claire et al. (2012)")
    )

    fun snapshot(ageSinceFormationMa: Double): Snapshot {
        val elapsed = ageSinceFormationMa.coerceIn(0.0, EvolutionClock.EARTH_AGE_MA)
        val lookbackMa = (EARTH_AGE_GA * 1000.0 - elapsed).coerceAtLeast(0.0)
        val lookbackGa = lookbackMa / 1000.0
        val sunAgeGa = (SUN_AGE_GA - lookbackGa).coerceAtLeast(0.0)

        val eon = when {
            lookbackMa >= 4031.0 -> Eon.HADEAN
            lookbackMa >= 2500.0 -> Eon.ARCHAEAN
            lookbackMa >= 538.8 -> Eon.PROTEROZOIC
            else -> Eon.PHANEROZOIC
        }

        val luminosity = goughLuminosityFraction(sunAgeGa)
        val modelStatus = when {
            sunAgeGa < 0.2 -> "Matahari sangat muda: model luminositas standar ini belum berlaku di sini"
            luminosity == null -> "Di luar rentang model"
            else -> "Estimasi bolometrik Gough (1981); bukan spektrum penuh"
        }

        val nearest = spectrumAnchors.minByOrNull { abs(it.lookbackGa - lookbackGa) }
            ?: spectrumAnchors.last()

        val (dayLength, dayStatus) = when {
            abs(lookbackGa - ARCHAEAN_LOOKBACK_GA) < 0.0005 ->
                ARCHAEAN_ROTATION_HOURS to "Nilai acuan dari Archaean of Arus v0.1"
            lookbackGa < 0.005 ->
                MODERN_SIDEREAL_DAY_HOURS to "Hari sideris modern sebagai acuan"
            else -> null to "Riwayat rotasi belum dihitung; tidak diinterpolasi sebagai fakta"
        }

        return Snapshot(
            ageSinceFormationMa = elapsed,
            lookbackMa = lookbackMa,
            eon = eon,
            sunAgeGa = sunAgeGa,
            solarLuminosityFraction = luminosity,
            solarModelStatus = modelStatus,
            nearestSpectrum = nearest,
            dayLengthHours = dayLength,
            dayLengthStatus = dayStatus
        )
    }

    /** Gough (1981) solar-luminosity approximation, only used for solar ages >= 0.2 Ga. */
    fun goughLuminosityFraction(sunAgeGa: Double): Double? {
        if (sunAgeGa < 0.2 || sunAgeGa > SUN_AGE_GA) return null
        val denominator = 1.0 + 0.4 * (1.0 - sunAgeGa / SUN_AGE_GA)
        return 1.0 / denominator
    }
}
