package org.terrachemica.app.core

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.exp

/** One spectral sample. Irradiance is expressed in W m^-2 nm^-1 at 1 AU. */
data class SolarSpectralPoint(
    val wavelengthNm: Double,
    val irradianceWm2Nm: Double
)

/**
 * A wavelength-resolved solar reference spectrum. The supplied VPL files are
 * irradiances at 1 AU with source units erg s^-1 cm^-2 nm^-1; the parser converts
 * those values to W m^-2 nm^-1 exactly once.
 */
data class SolarSpectrumData(
    val fileName: String,
    val referenceLookbackGa: Double,
    val wavelengthNm: DoubleArray,
    val irradianceWm2Nm: DoubleArray
) {
    init {
        require(fileName.isNotBlank())
        require(referenceLookbackGa >= 0.0 && referenceLookbackGa.isFinite())
        require(wavelengthNm.size == irradianceWm2Nm.size && wavelengthNm.size >= 2)
        for (i in wavelengthNm.indices) {
            require(wavelengthNm[i].isFinite() && wavelengthNm[i] > 0.0)
            require(irradianceWm2Nm[i].isFinite() && irradianceWm2Nm[i] >= 0.0)
            if (i > 0) require(wavelengthNm[i] > wavelengthNm[i - 1]) {
                "Wavelength harus meningkat ketat; duplikasi atau urutan salah pada indeks $i"
            }
        }
    }

    val minWavelengthNm: Double get() = wavelengthNm.first()
    val maxWavelengthNm: Double get() = wavelengthNm.last()
}

data class SolarSpectralBand(
    val id: String,
    val label: String,
    val minimumNm: Double,
    val maximumNm: Double
)

data class SolarBandIrradiance(
    val band: SolarSpectralBand,
    val irradianceWm2: Double
)

data class SolarRadiationReport(
    val sourceDescription: String,
    val lookbackGa: Double,
    /** Partial integral over the supplied dataset domain, not a bolometric TSI. */
    val coveredBandIrradianceWm2: Double,
    /** Sphere-average incoming flux, using illuminated disk / full sphere = 1/4. */
    val globalMeanIncomingWm2: Double,
    val bands: List<SolarBandIrradiance>,
    val bondAlbedo: Double?,
    val reflectedGlobalMeanWm2: Double?,
    val absorbedGlobalMeanWm2: Double?
)

data class BenchmarkBandDifference(
    val bandId: String,
    val expectedWm2: Double,
    val calculatedWm2: Double,
    val differenceWm2: Double,
    val withinTolerance: Boolean
)

data class ArchaeanSpectrumBenchmarkReport(
    val valid: Boolean,
    val bandDifferences: List<BenchmarkBandDifference>,
    val expectedTotalWm2: Double,
    val calculatedTotalWm2: Double,
    val totalDifferenceWm2: Double,
    val toleranceWm2: Double
)

/** Spectral integration, interpolation, and top-of-atmosphere geometry diagnostics. */
object SolarRadiationEngine {
    const val SOURCE_LOOKBACK_MIN_GA = 0.0
    const val SOURCE_LOOKBACK_MAX_GA = 4.4
    const val DATA_MIN_WAVELENGTH_NM = 0.5
    const val DATA_MAX_WAVELENGTH_NM = 2397.51
    const val ERG_PER_S_CM2_TO_W_PER_M2 = 1e-3

    val bands = listOf(
        SolarSpectralBand("XUV", "X-ray / XUV", 0.5, 10.0),
        SolarSpectralBand("EUV", "EUV", 10.0, 121.0),
        SolarSpectralBand("FUV", "FUV", 121.0, 200.0),
        SolarSpectralBand("UVC", "UV-C", 200.0, 280.0),
        SolarSpectralBand("UVB", "UV-B", 280.0, 315.0),
        SolarSpectralBand("UVA", "UV-A", 315.0, 400.0),
        SolarSpectralBand("VISIBLE", "Visible", 400.0, 700.0),
        SolarSpectralBand("NIR", "NIR", 700.0, 2397.51)
    )

    /** Parse the two-column VPL/Claire file and convert its cgs spectral units. */
    fun parseVplSpectrum(fileName: String, lookbackGa: Double, text: String): SolarSpectrumData {
        val lines = text.lineSequence().toList()
        val header = lines.firstOrNull { it.trimStart().startsWith("#") && it.contains("Flux", ignoreCase = true) }
            ?: error("Header kolom Flux tidak ditemukan dalam $fileName")
        require(header.contains("erg/s/cm2/nm", ignoreCase = true)) {
            "Satuan flux tidak dikenal pada $fileName: $header"
        }

        val wavelengths = ArrayList<Double>(26_200)
        val fluxes = ArrayList<Double>(26_200)
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val columns = line.split(Regex("\\s+"))
            require(columns.size >= 2) { "Baris data ${index + 1} tidak memiliki dua kolom" }
            val wavelength = columns[0].toDoubleOrNull()
                ?: error("Wavelength tidak valid pada baris ${index + 1}")
            val sourceFlux = columns[1].toDoubleOrNull()
                ?: error("Flux tidak valid pada baris ${index + 1}")
            require(sourceFlux >= 0.0 && sourceFlux.isFinite()) { "Flux negatif/tidak finite pada baris ${index + 1}" }
            wavelengths += wavelength
            fluxes += sourceFlux * ERG_PER_S_CM2_TO_W_PER_M2
        }
        require(wavelengths.size >= 2) { "Spektrum $fileName terlalu pendek" }
        return SolarSpectrumData(
            fileName = fileName,
            referenceLookbackGa = lookbackGa,
            wavelengthNm = wavelengths.toDoubleArray(),
            irradianceWm2Nm = fluxes.toDoubleArray()
        )
    }

    /**
     * Integrate with linear interpolation at both requested boundaries. This avoids
     * dropping the fractional edge intervals when a boundary lies between samples.
     */
    fun integrateBand(
        spectrum: SolarSpectrumData,
        minimumNm: Double,
        maximumNm: Double
    ): Double {
        require(minimumNm.isFinite() && maximumNm.isFinite() && minimumNm < maximumNm)
        require(minimumNm >= spectrum.minWavelengthNm && maximumNm <= spectrum.maxWavelengthNm) {
            "Domain integrasi $minimumNm–$maximumNm nm di luar cakupan ${spectrum.minWavelengthNm}–${spectrum.maxWavelengthNm} nm"
        }

        var x0 = minimumNm
        var y0 = sampleAt(spectrum, minimumNm)
        var integral = 0.0
        for (i in spectrum.wavelengthNm.indices) {
            val x1 = spectrum.wavelengthNm[i]
            if (x1 <= minimumNm || x1 >= maximumNm) continue
            val y1 = spectrum.irradianceWm2Nm[i]
            integral += (y0 + y1) * 0.5 * (x1 - x0)
            x0 = x1
            y0 = y1
        }
        val yEnd = sampleAt(spectrum, maximumNm)
        integral += (y0 + yEnd) * 0.5 * (maximumNm - x0)
        require(integral >= 0.0 && integral.isFinite()) { "Integral spektral tidak valid" }
        return integral
    }

    fun interpolateInTime(
        olderSpectrum: SolarSpectrumData,
        newerSpectrum: SolarSpectrumData,
        targetLookbackGa: Double
    ): SolarSpectrumData {
        require(olderSpectrum.referenceLookbackGa > newerSpectrum.referenceLookbackGa) {
            "Spektrum pertama harus merupakan jangkar yang lebih tua (lookback lebih besar)"
        }
        require(targetLookbackGa in newerSpectrum.referenceLookbackGa..olderSpectrum.referenceLookbackGa) {
            "Target waktu berada di luar dua jangkar spektrum; ekstrapolasi tidak diizinkan"
        }
        if (abs(targetLookbackGa - olderSpectrum.referenceLookbackGa) < 1e-12) return olderSpectrum
        if (abs(targetLookbackGa - newerSpectrum.referenceLookbackGa) < 1e-12) return newerSpectrum

        val sharedMinimum = maxOf(olderSpectrum.minWavelengthNm, newerSpectrum.minWavelengthNm)
        val sharedMaximum = minOf(olderSpectrum.maxWavelengthNm, newerSpectrum.maxWavelengthNm)
        require(sharedMinimum <= DATA_MIN_WAVELENGTH_NM && sharedMaximum >= DATA_MAX_WAVELENGTH_NM) {
            "Domain bersama dua spektrum tidak mencakup seluruh rentang benchmark ${DATA_MIN_WAVELENGTH_NM}–${DATA_MAX_WAVELENGTH_NM} nm"
        }
        // Use the older snapshot's grid and linearly resample the newer snapshot in
        // wavelength before the time interpolation. This permits different native grids.
        val commonBuffer = DoubleArray(olderSpectrum.wavelengthNm.size)
        var commonCount = 0
        for (wavelength in olderSpectrum.wavelengthNm) {
            if (wavelength >= sharedMinimum && wavelength <= sharedMaximum) {
                commonBuffer[commonCount++] = wavelength
            }
        }
        require(commonCount >= 2)
        val commonWavelengths = commonBuffer.copyOf(commonCount)

        // Linear interpolation in log-flux preserves multiplicative spectral evolution.
        // If either endpoint is exactly zero, use linear interpolation for that sample.
        val fractionFromOlder = (olderSpectrum.referenceLookbackGa - targetLookbackGa) /
            (olderSpectrum.referenceLookbackGa - newerSpectrum.referenceLookbackGa)
        val values = DoubleArray(commonWavelengths.size) { i ->
            val wavelength = commonWavelengths[i]
            val older = sampleAt(olderSpectrum, wavelength)
            val newer = sampleAt(newerSpectrum, wavelength)
            if (older > 0.0 && newer > 0.0) {
                exp(ln(older) * (1.0 - fractionFromOlder) + ln(newer) * fractionFromOlder)
            } else {
                older * (1.0 - fractionFromOlder) + newer * fractionFromOlder
            }
        }
        return SolarSpectrumData(
            fileName = "INTERPOLATED:${olderSpectrum.fileName}:${newerSpectrum.fileName}",
            referenceLookbackGa = targetLookbackGa,
            wavelengthNm = commonWavelengths,
            irradianceWm2Nm = values
        )
    }

    /**
     * Create a radiative input diagnostic at 1 AU. This is not an atmosphere solver:
     * absorption and Bond albedo must be supplied independently before absorbed flux
     * can be evaluated.
     */
    fun calculateAtTopOfAtmosphere(
        spectrum: SolarSpectrumData,
        bondAlbedo: Double? = null
    ): SolarRadiationReport {
        require(bondAlbedo == null || (bondAlbedo.isFinite() && bondAlbedo in 0.0..1.0)) {
            "Bond albedo harus berada pada rentang 0..1"
        }
        val bandResults = bands.map { band ->
            SolarBandIrradiance(band, integrateBand(spectrum, band.minimumNm, band.maximumNm))
        }
        val total = bandResults.sumOf { it.irradianceWm2 }
        val globalMean = total / 4.0
        val reflected = bondAlbedo?.let { globalMean * it }
        val absorbed = bondAlbedo?.let { globalMean * (1.0 - it) }
        return SolarRadiationReport(
            sourceDescription = spectrum.fileName,
            lookbackGa = spectrum.referenceLookbackGa,
            coveredBandIrradianceWm2 = total,
            globalMeanIncomingWm2 = globalMean,
            bands = bandResults,
            bondAlbedo = bondAlbedo,
            reflectedGlobalMeanWm2 = reflected,
            absorbedGlobalMeanWm2 = absorbed
        )
    }

    /** Compare the 3.8 Ga spectrum against the exact rounded table values in the supplied PDF. */
    fun validateArchaeanPdfBenchmark(report: SolarRadiationReport, toleranceWm2: Double = 5e-4): ArchaeanSpectrumBenchmarkReport {
        require(abs(report.lookbackGa - 3.8) < 1e-9) { "Benchmark hanya berlaku pada snapshot 3,8 Ga" }
        val expected = mapOf(
            "XUV" to 0.005813707,
            "EUV" to 0.035405974,
            "FUV" to 0.148874598,
            "UVC" to 4.279050209,
            "UVB" to 11.682507388,
            "UVA" to 59.617487306,
            "VISIBLE" to 395.947185897,
            "NIR" to 527.954651519
        )
        val differences = report.bands.map { actual ->
            val target = expected.getValue(actual.band.id)
            val difference = actual.irradianceWm2 - target
            BenchmarkBandDifference(actual.band.id, target, actual.irradianceWm2, difference, abs(difference) <= toleranceWm2)
        }
        val expectedTotal = 999.670976598
        val totalDifference = report.coveredBandIrradianceWm2 - expectedTotal
        return ArchaeanSpectrumBenchmarkReport(
            valid = differences.all { it.withinTolerance } && abs(totalDifference) <= toleranceWm2,
            bandDifferences = differences,
            expectedTotalWm2 = expectedTotal,
            calculatedTotalWm2 = report.coveredBandIrradianceWm2,
            totalDifferenceWm2 = totalDifference,
            toleranceWm2 = toleranceWm2
        )
    }

    private fun sampleAt(spectrum: SolarSpectrumData, wavelength: Double): Double {
        val xs = spectrum.wavelengthNm
        val ys = spectrum.irradianceWm2Nm
        val index = xs.binarySearch(wavelength)
        if (index >= 0) return ys[index]
        val insertionPoint = -index - 1
        require(insertionPoint in 1 until xs.size) { "Tidak dapat menginterpolasi wavelength $wavelength nm" }
        val left = insertionPoint - 1
        val ratio = (wavelength - xs[left]) / (xs[insertionPoint] - xs[left])
        return ys[left] + (ys[insertionPoint] - ys[left]) * ratio
    }
}
