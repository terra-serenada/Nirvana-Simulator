package org.terrachemica.app.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** Optical depth assigned to one effective atmospheric layer for each source band. */
data class BandOpticalDepths(
    val bandId: String,
    val opticalDepthByLayerTopToBottom: List<Pair<String, Double>>
) {
    init {
        require(bandId.isNotBlank())
        require(opticalDepthByLayerTopToBottom.isNotEmpty())
        require(opticalDepthByLayerTopToBottom.map { it.first }.distinct().size == opticalDepthByLayerTopToBottom.size)
        opticalDepthByLayerTopToBottom.forEach { (layer, tau) ->
            require(layer.isNotBlank() && tau.isFinite() && tau >= 0.0)
        }
    }
}

data class ShortwaveOpticalModel(
    val modelId: String,
    val bandOptics: Map<String, BandOpticalDepths>,
    /** Effective lower-boundary reflectance; not a literal bare-ground surface albedo. */
    val lowerBoundaryReflectanceByBand: Map<String, Double>,
    /** Effective cosine of zenith used for the global-mean two-pass approximation. */
    val effectiveCosineZenith: Double,
    val scientificScope: String
) {
    init {
        require(modelId.isNotBlank())
        require(effectiveCosineZenith.isFinite() && effectiveCosineZenith in 0.05..1.0)
        require(bandOptics.isNotEmpty())
        bandOptics.forEach { (id, optics) -> require(id == optics.bandId) }
        lowerBoundaryReflectanceByBand.forEach { (id, reflectance) ->
            require(id in bandOptics)
            require(reflectance.isFinite() && reflectance in 0.0..1.0)
        }
        require(lowerBoundaryReflectanceByBand.keys.containsAll(bandOptics.keys))
    }
}

data class LayerShortwaveBudget(
    val layerId: String,
    val absorbedDownWm2: Double,
    val absorbedUpWm2: Double
) {
    val absorbedTotalWm2: Double get() = absorbedDownWm2 + absorbedUpWm2
}

data class BandShortwaveBudget(
    val bandId: String,
    val incomingGlobalMeanWm2: Double,
    val layers: List<LayerShortwaveBudget>,
    val surfaceAbsorbedWm2: Double,
    val topOfAtmosphereReflectedWm2: Double,
    val energyResidualWm2: Double
) {
    val atmosphereAbsorbedWm2: Double get() = layers.sumOf { it.absorbedTotalWm2 }
    val totalAbsorbedWm2: Double get() = atmosphereAbsorbedWm2 + surfaceAbsorbedWm2
}

data class ShortwaveColumnBudget(
    val sourceDescription: String,
    val lookbackGa: Double,
    val modelId: String,
    val bandBudgets: List<BandShortwaveBudget>,
    val incomingGlobalMeanWm2: Double,
    val atmosphereAbsorbedWm2: Double,
    val surfaceAbsorbedWm2: Double,
    val reflectedToSpaceWm2: Double,
    val energyResidualWm2: Double,
    val scientificScope: String
)

data class GreyLongwaveEquilibrium(
    val solarAgeGa: Double,
    val solarLuminosityFraction: Double,
    val totalSolarIrradianceWm2: Double,
    val globalMeanIncomingSolarWm2: Double,
    val reflectedShortwaveWm2: Double,
    val atmosphereAbsorbedShortwaveWm2: Double,
    val surfaceAbsorbedShortwaveWm2: Double,
    val atmosphericInfraredEmissivity: Double,
    val surfaceTemperatureK: Double,
    val effectiveAtmosphereTemperatureK: Double,
    val surfaceUpwardLongwaveWm2: Double,
    val atmosphereDownwardLongwaveWm2: Double,
    val outgoingLongwaveToSpaceWm2: Double,
    val surfaceEnergyResidualWm2: Double,
    val atmosphereEnergyResidualWm2: Double,
    val topOfAtmosphereEnergyResidualWm2: Double,
    val calibrationLabel: String
)

data class RadiativeCalibration(
    val totalSolarIrradianceModernWm2: Double,
    val reflectedFraction: Double,
    val atmosphereAbsorbedFraction: Double,
    val surfaceAbsorbedFraction: Double,
    val targetModernSurfaceTemperatureK: Double,
    val effectiveCosineZenith: Double
) {
    init {
        require(totalSolarIrradianceModernWm2.isFinite() && totalSolarIrradianceModernWm2 > 0.0)
        require(reflectedFraction in 0.0..1.0)
        require(atmosphereAbsorbedFraction in 0.0..1.0)
        require(surfaceAbsorbedFraction in 0.0..1.0)
        require(abs(reflectedFraction + atmosphereAbsorbedFraction + surfaceAbsorbedFraction - 1.0) < 1e-12)
        require(targetModernSurfaceTemperatureK.isFinite() && targetModernSurfaceTemperatureK > 0.0)
        require(effectiveCosineZenith.isFinite() && effectiveCosineZenith in 0.05..1.0)
    }
}

/**
 * v0.5 atmosphere prototype.
 *
 * Shortwave: absorption-only Beer-Lambert passes downward and upward through the
 * supplied effective layers. Scattering, clouds, aerosols and multiple reflections
 * are deliberately not represented as separate physical processes.
 *
 * Longwave: one effective grey slab in radiative equilibrium. Its reference
 * parameters are calibrated to a modern global-mean energy-budget closure. Holding
 * those parameters fixed in deep time is a solar-only sensitivity experiment, not
 * a palaeoclimate reconstruction.
 */
object AtmosphericRadiativeTransferEngine {
    const val STEFAN_BOLTZMANN_W_M2_K4 = 5.670374419e-8
    const val MODERN_TSI_W_M2 = 1361.0
    const val MODERN_GLOBAL_MEAN_INCOMING_W_M2 = MODERN_TSI_W_M2 / 4.0
    const val DEFAULT_MODERN_SURFACE_TEMPERATURE_K = 288.15
    const val DEFAULT_EFFECTIVE_COSINE_ZENITH = 0.5
    const val ENERGY_CLOSURE_TOLERANCE_W_M2 = 1e-8

    /**
     * Rounded modern energy-budget partition used as an explicit effective calibration:
     * 29% reflected, 23% absorbed in atmosphere, 48% absorbed at surface.
     * Source: NASA Earth Observatory energy-budget explainer.
     */
    val modernCalibration = RadiativeCalibration(
        totalSolarIrradianceModernWm2 = MODERN_TSI_W_M2,
        reflectedFraction = 0.29,
        atmosphereAbsorbedFraction = 0.23,
        surfaceAbsorbedFraction = 0.48,
        targetModernSurfaceTemperatureK = DEFAULT_MODERN_SURFACE_TEMPERATURE_K,
        effectiveCosineZenith = DEFAULT_EFFECTIVE_COSINE_ZENITH
    )

    data class EffectiveModernPreset(
        val shortwaveModel: ShortwaveOpticalModel,
        val longwaveEmissivity: Double,
        val downTransmittance: Double,
        val effectiveLowerBoundaryReflectance: Double,
        val sourceFractions: RadiativeCalibration
    )

    fun buildModernEffectivePreset(bandIds: Set<String>): EffectiveModernPreset {
        require(bandIds.isNotEmpty())
        val calibration = modernCalibration
        val transmittance = (calibration.surfaceAbsorbedFraction +
            sqrt(calibration.surfaceAbsorbedFraction.pow(2) + 4.0 * calibration.reflectedFraction)) / 2.0
        val lowerReflectance = 1.0 - calibration.surfaceAbsorbedFraction / transmittance
        val totalOpticalDepth = -calibration.effectiveCosineZenith * ln(transmittance)
        val tauPerLayer = listOf("EFFECTIVE_ATMOSPHERE" to totalOpticalDepth)
        val optics = bandIds.associateWith { bandId -> BandOpticalDepths(bandId, tauPerLayer) }
        val reflectance = bandIds.associateWith { lowerReflectance }
        val model = ShortwaveOpticalModel(
            modelId = "MODERN_GLOBAL_EFFECTIVE_ABSORPTION_ONLY_V1",
            bandOptics = optics,
            lowerBoundaryReflectanceByBand = reflectance,
            effectiveCosineZenith = calibration.effectiveCosineZenith,
            scientificScope = "Kalibrasi global modern efektif; absorpsi Beer-Lambert satu lapis, tanpa pemisahan hamburan, awan, aerosol, dan refleksi multipel. Reflektansi batas bawah merangkum semua refleksi yang tidak diselesaikan eksplisit."
        )

        val q = calibration.totalSolarIrradianceModernWm2 / 4.0
        val surfaceShortwave = q * calibration.surfaceAbsorbedFraction
        val atmosphereShortwave = q * calibration.atmosphereAbsorbedFraction
        val sigmaTs4 = STEFAN_BOLTZMANN_W_M2_K4 * calibration.targetModernSurfaceTemperatureK.pow(4)
        val emissivity = 2.0 - (2.0 * surfaceShortwave + atmosphereShortwave) / sigmaTs4
        require(emissivity in 0.0..1.0 && emissivity.isFinite()) {
            "Kalibrasi longwave menghasilkan emisivitas di luar rentang fisik 0..1"
        }
        return EffectiveModernPreset(model, emissivity, transmittance, lowerReflectance, calibration)
    }

    fun calculateShortwave(
        spectrum: SolarRadiationReport,
        model: ShortwaveOpticalModel
    ): ShortwaveColumnBudget {
        require(spectrum.bands.isNotEmpty())
        require(spectrum.bands.all { it.band.id in model.bandOptics }) {
            "Optical model tidak menyediakan parameter untuk semua pita spektrum"
        }

        val bandResults = spectrum.bands.map { sourceBand ->
            val incoming = sourceBand.irradianceWm2 / 4.0
            require(incoming.isFinite() && incoming >= 0.0)
            val layerDefinitions = model.bandOptics.getValue(sourceBand.band.id).opticalDepthByLayerTopToBottom
            val downAbsorption = DoubleArray(layerDefinitions.size)
            val upAbsorption = DoubleArray(layerDefinitions.size)
            val transmittances = DoubleArray(layerDefinitions.size) { i ->
                exp(-layerDefinitions[i].second / model.effectiveCosineZenith)
            }

            var downFlux = incoming
            for (i in layerDefinitions.indices) {
                val absorbed = downFlux * (1.0 - transmittances[i])
                downAbsorption[i] = absorbed
                downFlux -= absorbed
            }

            val reflectance = model.lowerBoundaryReflectanceByBand.getValue(sourceBand.band.id)
            val surfaceAbsorbed = downFlux * (1.0 - reflectance)
            var upwardFlux = downFlux * reflectance
            for (i in layerDefinitions.indices.reversed()) {
                val absorbed = upwardFlux * (1.0 - transmittances[i])
                upAbsorption[i] = absorbed
                upwardFlux -= absorbed
            }
            val reflectedToSpace = upwardFlux
            val layerBudgets = layerDefinitions.indices.map { i ->
                LayerShortwaveBudget(layerDefinitions[i].first, downAbsorption[i], upAbsorption[i])
            }
            val accounted = layerBudgets.sumOf { it.absorbedTotalWm2 } + surfaceAbsorbed + reflectedToSpace
            BandShortwaveBudget(
                bandId = sourceBand.band.id,
                incomingGlobalMeanWm2 = incoming,
                layers = layerBudgets,
                surfaceAbsorbedWm2 = surfaceAbsorbed,
                topOfAtmosphereReflectedWm2 = reflectedToSpace,
                energyResidualWm2 = incoming - accounted
            )
        }

        return ShortwaveColumnBudget(
            sourceDescription = spectrum.sourceDescription,
            lookbackGa = spectrum.lookbackGa,
            modelId = model.modelId,
            bandBudgets = bandResults,
            incomingGlobalMeanWm2 = bandResults.sumOf { it.incomingGlobalMeanWm2 },
            atmosphereAbsorbedWm2 = bandResults.sumOf { it.atmosphereAbsorbedWm2 },
            surfaceAbsorbedWm2 = bandResults.sumOf { it.surfaceAbsorbedWm2 },
            reflectedToSpaceWm2 = bandResults.sumOf { it.topOfAtmosphereReflectedWm2 },
            energyResidualWm2 = bandResults.sumOf { it.energyResidualWm2 },
            scientificScope = model.scientificScope
        )
    }

    /**
     * A one-layer grey radiative-equilibrium sensitivity using Gough's bolometric
     * luminosity fraction supplied by EarthEvolutionModel. No sensible/latent heat,
     * ocean heat storage, clouds, atmospheric circulation or changing palaeo-optics.
     */
    fun calculateGreyLongwaveEquilibrium(
        solarAgeGa: Double,
        luminosityFraction: Double,
        preset: EffectiveModernPreset
    ): GreyLongwaveEquilibrium {
        require(solarAgeGa.isFinite() && solarAgeGa >= 0.0)
        require(luminosityFraction.isFinite() && luminosityFraction > 0.0)
        val calibration = preset.sourceFractions
        val tsi = calibration.totalSolarIrradianceModernWm2 * luminosityFraction
        val incoming = tsi / 4.0
        val reflected = incoming * calibration.reflectedFraction
        val atmosphereAbsorbed = incoming * calibration.atmosphereAbsorbedFraction
        val surfaceAbsorbed = incoming * calibration.surfaceAbsorbedFraction
        val epsilon = preset.longwaveEmissivity
        require(epsilon > 0.0 && epsilon < 1.0)

        // Solve the surface and atmospheric one-layer radiative equilibrium equations:
        // sigma*Ts^4 - eps*sigma*Ta^4 = SW_surface
        // -eps*sigma*Ts^4 + 2*eps*sigma*Ta^4 = SW_atmosphere
        val denominator = 2.0 - epsilon
        val surfaceEmission = (2.0 * surfaceAbsorbed + atmosphereAbsorbed) / denominator
        val atmosphereEmission = (atmosphereAbsorbed + epsilon * surfaceAbsorbed) / (epsilon * denominator)
        val surfaceTemperature = (surfaceEmission / STEFAN_BOLTZMANN_W_M2_K4).pow(0.25)
        val atmosphereTemperature = (atmosphereEmission / STEFAN_BOLTZMANN_W_M2_K4).pow(0.25)
        val atmosphereDownLongwave = epsilon * atmosphereEmission
        val outgoingLongwave = (1.0 - epsilon) * surfaceEmission + epsilon * atmosphereEmission
        val surfaceResidual = surfaceAbsorbed + atmosphereDownLongwave - surfaceEmission
        val atmosphereResidual = atmosphereAbsorbed + epsilon * surfaceEmission - 2.0 * atmosphereDownLongwave
        val toaResidual = incoming - reflected - outgoingLongwave

        return GreyLongwaveEquilibrium(
            solarAgeGa = solarAgeGa,
            solarLuminosityFraction = luminosityFraction,
            totalSolarIrradianceWm2 = tsi,
            globalMeanIncomingSolarWm2 = incoming,
            reflectedShortwaveWm2 = reflected,
            atmosphereAbsorbedShortwaveWm2 = atmosphereAbsorbed,
            surfaceAbsorbedShortwaveWm2 = surfaceAbsorbed,
            atmosphericInfraredEmissivity = epsilon,
            surfaceTemperatureK = surfaceTemperature,
            effectiveAtmosphereTemperatureK = atmosphereTemperature,
            surfaceUpwardLongwaveWm2 = surfaceEmission,
            atmosphereDownwardLongwaveWm2 = atmosphereDownLongwave,
            outgoingLongwaveToSpaceWm2 = outgoingLongwave,
            surfaceEnergyResidualWm2 = surfaceResidual,
            atmosphereEnergyResidualWm2 = atmosphereResidual,
            topOfAtmosphereEnergyResidualWm2 = toaResidual,
            calibrationLabel = "Parameter efektif modern ditahan tetap; eksperimen sensitivitas pengaruh luminositas Matahari, bukan rekonstruksi paleoklima."
        )
    }

    fun validateEnergyClosure(report: ShortwaveColumnBudget, toleranceWm2: Double = ENERGY_CLOSURE_TOLERANCE_W_M2): Boolean =
        abs(report.energyResidualWm2) <= toleranceWm2 &&
            report.bandBudgets.all { abs(it.energyResidualWm2) <= toleranceWm2 }

    fun validateEnergyClosure(report: GreyLongwaveEquilibrium, toleranceWm2: Double = ENERGY_CLOSURE_TOLERANCE_W_M2): Boolean =
        abs(report.surfaceEnergyResidualWm2) <= toleranceWm2 &&
            abs(report.atmosphereEnergyResidualWm2) <= toleranceWm2 &&
            abs(report.topOfAtmosphereEnergyResidualWm2) <= toleranceWm2
}
