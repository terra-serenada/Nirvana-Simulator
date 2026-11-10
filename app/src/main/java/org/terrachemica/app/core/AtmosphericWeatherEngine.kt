package org.terrachemica.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Climate forcing supplied by Terra Chemica's one-layer grey radiative model. */
data class WeatherForcing(
    val solarLuminosityFraction: Double,
    val equilibriumSurfaceTemperatureK: Double
) {
    init {
        require(solarLuminosityFraction.isFinite() && solarLuminosityFraction > 0.0)
        require(equilibriumSurfaceTemperatureK.isFinite() && equilibriumSurfaceTemperatureK in 150.0..380.0)
    }
}

enum class WeatherRegime(val label: String) {
    CLEAR("Cerah / sedikit awan"),
    CLOUD_FORMATION("Awan berkembang"),
    RAIN("Hujan"),
    THUNDERSTORM("Badai konvektif")
}

/**
 * State of a single representative atmospheric column. Water units are mm = kg m^-2.
 * The same column is visualized over the globe; it is not a spatial global forecast.
 */
data class AtmosphericWeatherState(
    val regime: WeatherRegime,
    val airTemperatureK: Double,
    val relativeHumidity: Double,
    val waterVaporColumnMm: Double,
    val cloudLiquidWaterMm: Double,
    val cloudFraction: Double,
    val precipitationRateMmPerHour: Double,
    val convectionIndex: Double,
    val lightningEventCount: Long,
    val accumulatedRainMm: Double,
    val waterBalanceResidualMm: Double,
    val localHour: Double
)

/**
 * Reduced, mass-conserving water-cycle / convection demonstrator.
 *
 * Coupling: Gough solar-luminosity fraction -> the existing grey radiative-equilibrium
 * surface temperature -> saturation capacity and surface evaporation -> vapor/cloud
 * condensation -> precipitation returned to the surface reservoir. A simple
 * convective proxy controls charge build-up and lightning events.
 *
 * It is intentionally not a weather forecast or validated paleoclimate model. The
 * representative column uses an explicit modern-ocean-equivalent surface-water
 * reservoir and fixed effective air-column mass; neither is reconstructed through time.
 */
class AtmosphericWeatherEngine {
    companion object {
        private const val MODERN_TSI_W_M2 = 1361.0
        private const val OCEAN_EQUIVALENT_WATER_MM = 2_650_000.0
        private const val EFFECTIVE_AIR_COLUMN_KG_M2 = 2_500.0
        private const val PRESSURE_PA = 100_000.0
        private const val MAX_INTERNAL_STEP_SECONDS = 120.0
        private const val MIN_CLOUD_WATER_FOR_RAIN_MM = 0.45
        private const val LIGHTNING_THRESHOLD = 0.10
        private const val CHARGE_BUILD_THRESHOLD = 0.18
        private const val CONVECTIVE_LIGHTNING_INDEX = 0.62
        private const val MIN_STORM_CLOUD_COVER = 0.25
    }

    private var modelTimeSeconds = 10.0 * 3600.0
    private var surfaceWaterMm = OCEAN_EQUIVALENT_WATER_MM
    private var vaporMm = 0.0
    private var cloudWaterMm = 0.0
    private var lightningCharge = 0.0
    private var lightningEvents = 0L
    private var accumulatedRainMm = 0.0
    private var initialized = false
    private var state: AtmosphericWeatherState? = null

    fun currentState(): AtmosphericWeatherState? = state

    fun reset() {
        modelTimeSeconds = 10.0 * 3600.0
        surfaceWaterMm = OCEAN_EQUIVALENT_WATER_MM
        vaporMm = 0.0
        cloudWaterMm = 0.0
        lightningCharge = 0.0
        lightningEvents = 0L
        accumulatedRainMm = 0.0
        initialized = false
        state = null
    }

    /**
     * Advances the weather clock. The caller deliberately maps 1 real second to
     * 1 model-weather hour so clouds and storms can be watched in a phone session.
     * This accelerator is separate from Earth's geological clock.
     */
    fun advance(weatherSeconds: Double, forcing: WeatherForcing): AtmosphericWeatherState {
        require(weatherSeconds.isFinite() && weatherSeconds >= 0.0)
        if (!initialized) initialize(forcing)
        var remaining = weatherSeconds.coerceAtMost(6.0 * 3600.0)
        var residual = 0.0
        while (remaining > 1e-9) {
            val dt = min(remaining, MAX_INTERNAL_STEP_SECONDS)
            residual = step(dt, forcing)
            remaining -= dt
        }
        return buildState(forcing, residual).also { state = it }
    }

    private fun initialize(forcing: WeatherForcing) {
        val airTemp = forcing.equilibriumSurfaceTemperatureK - 4.0
        vaporMm = saturationColumnMm(airTemp) * 0.82
        cloudWaterMm = 0.18
        initialized = true
    }

    /** Returns the water-budget closure residual for this step. */
    private fun step(dtSeconds: Double, forcing: WeatherForcing): Double {
        val before = surfaceWaterMm + vaporMm + cloudWaterMm
        val dtHours = dtSeconds / 3600.0
        val hour = (modelTimeSeconds % 86_400.0) / 3600.0
        val daylightWave = sin(2.0 * PI * (hour - 6.0) / 24.0)
        val sunlitFraction = daylightWave.coerceAtLeast(0.0)
        val localSolarWm2 = MODERN_TSI_W_M2 * forcing.solarLuminosityFraction * sunlitFraction

        // A small diurnal perturbation around the grey-model global equilibrium temperature.
        val surfaceLocalK = forcing.equilibriumSurfaceTemperatureK + 2.5 * daylightWave
        val airTemperatureK = forcing.equilibriumSurfaceTemperatureK - 4.0 + 1.5 * daylightWave
        val saturationMm = saturationColumnMm(airTemperatureK).coerceAtLeast(0.05)
        val relativeHumidity = (vaporMm / saturationMm).coerceIn(0.0, 1.25)

        // Bulk evaporation proxy: peaks in daylight and is capped at a few mm per day.
        val evaporationRateMmPerHour = 0.14 * (localSolarWm2 / 1000.0).coerceIn(0.0, 1.2)
        val evaporation = min(surfaceWaterMm, evaporationRateMmPerHour * dtHours)
        surfaceWaterMm -= evaporation
        vaporMm += evaporation

        // Condensation activates near saturation. Condensate is transferred, not created.
        val condensationRateMmPerHour = max(0.0, relativeHumidity - 0.76) * 4.5
        val condensation = min(vaporMm, condensationRateMmPerHour * dtHours)
        vaporMm -= condensation
        cloudWaterMm += condensation

        val cloudEvaporationRate = when {
            relativeHumidity < 0.72 -> 0.035
            relativeHumidity < 0.82 -> 0.012
            else -> 0.002
        }
        val cloudEvaporation = min(cloudWaterMm, cloudWaterMm * cloudEvaporationRate * dtHours)
        cloudWaterMm -= cloudEvaporation
        vaporMm += cloudEvaporation

        val updatedSaturationMm = saturationColumnMm(airTemperatureK).coerceAtLeast(0.05)
        val updatedHumidity = (vaporMm / updatedSaturationMm).coerceIn(0.0, 1.25)
        val moistureIndex = ((updatedHumidity - 0.48) / 0.40).coerceIn(0.0, 1.0)
        val cloudLoadIndex = (cloudWaterMm / 2.8).coerceIn(0.0, 1.0)
        val solarHeatingIndex = (localSolarWm2 / 950.0).coerceIn(0.0, 1.0)
        val thermalContrastK = surfaceLocalK - airTemperatureK
        val thermalIndex = ((thermalContrastK - 1.5) / 6.5).coerceIn(0.0, 1.0)
        val convection = (0.35 * solarHeatingIndex + 0.35 * moistureIndex +
            0.20 * cloudLoadIndex + 0.10 * thermalIndex).coerceIn(0.0, 1.0)

        val rainRate = if (cloudWaterMm > MIN_CLOUD_WATER_FOR_RAIN_MM && convection > 0.25) {
            ((cloudWaterMm - MIN_CLOUD_WATER_FOR_RAIN_MM) * (0.45 + 1.10 * convection))
                .coerceIn(0.0, 18.0)
        } else 0.0
        val precipitation = min(cloudWaterMm, rainRate * dtHours)
        cloudWaterMm -= precipitation
        surfaceWaterMm += precipitation
        accumulatedRainMm += precipitation

        val cloudCoverForCharge = (0.58 * ((updatedHumidity - 0.58) / 0.42).coerceIn(0.0, 1.0) +
            0.42 * cloudLoadIndex).coerceIn(0.0, 1.0)
        if (convection >= CONVECTIVE_LIGHTNING_INDEX && cloudCoverForCharge >= MIN_STORM_CLOUD_COVER) {
            // Electrical charge is a nondimensional proxy accumulating only in moist, convective cloud.
            lightningCharge += dtHours * max(0.0, convection * cloudCoverForCharge - CHARGE_BUILD_THRESHOLD)
        } else {
            lightningCharge = (lightningCharge - dtHours * 0.001).coerceAtLeast(0.0)
        }
        if (lightningCharge >= LIGHTNING_THRESHOLD) {
            lightningEvents += 1
            lightningCharge = 0.035
        }

        modelTimeSeconds += dtSeconds
        val after = surfaceWaterMm + vaporMm + cloudWaterMm
        return before - after
    }

    private fun buildState(forcing: WeatherForcing, residual: Double): AtmosphericWeatherState {
        val hour = (modelTimeSeconds % 86_400.0) / 3600.0
        val wave = sin(2.0 * PI * (hour - 6.0) / 24.0)
        val airTemperatureK = forcing.equilibriumSurfaceTemperatureK - 4.0 + 1.5 * wave
        val saturationMm = saturationColumnMm(airTemperatureK).coerceAtLeast(0.05)
        val rh = (vaporMm / saturationMm).coerceIn(0.0, 1.25)
        val cloudFraction = (0.58 * ((rh - 0.58) / 0.42).coerceIn(0.0, 1.0) +
            0.42 * (cloudWaterMm / 2.8).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        val moistureIndex = ((rh - 0.48) / 0.40).coerceIn(0.0, 1.0)
        val cloudLoadIndex = (cloudWaterMm / 2.8).coerceIn(0.0, 1.0)
        val sunlitFraction = sin(2.0 * PI * (hour - 6.0) / 24.0).coerceAtLeast(0.0)
        val localSolarWm2 = MODERN_TSI_W_M2 * forcing.solarLuminosityFraction * sunlitFraction
        val solarHeatingIndex = (localSolarWm2 / 950.0).coerceIn(0.0, 1.0)
        val surfaceLocalK = forcing.equilibriumSurfaceTemperatureK + 2.5 * wave
        val thermalContrastK = surfaceLocalK - airTemperatureK
        val thermalIndex = ((thermalContrastK - 1.5) / 6.5).coerceIn(0.0, 1.0)
        val convection = (0.35 * solarHeatingIndex + 0.35 * moistureIndex +
            0.20 * cloudLoadIndex + 0.10 * thermalIndex).coerceIn(0.0, 1.0)
        val rainRate = if (cloudWaterMm > MIN_CLOUD_WATER_FOR_RAIN_MM && convection > 0.25) {
            ((cloudWaterMm - MIN_CLOUD_WATER_FOR_RAIN_MM) * (0.45 + 1.10 * convection))
                .coerceIn(0.0, 18.0)
        } else 0.0
        val regime = when {
            convection >= CONVECTIVE_LIGHTNING_INDEX && cloudFraction >= MIN_STORM_CLOUD_COVER -> WeatherRegime.THUNDERSTORM
            rainRate >= 0.05 -> WeatherRegime.RAIN
            cloudFraction >= 0.16 -> WeatherRegime.CLOUD_FORMATION
            else -> WeatherRegime.CLEAR
        }
        return AtmosphericWeatherState(
            regime = regime,
            airTemperatureK = airTemperatureK,
            relativeHumidity = rh,
            waterVaporColumnMm = vaporMm,
            cloudLiquidWaterMm = cloudWaterMm,
            cloudFraction = cloudFraction,
            precipitationRateMmPerHour = rainRate,
            convectionIndex = convection,
            lightningEventCount = lightningEvents,
            accumulatedRainMm = accumulatedRainMm,
            waterBalanceResidualMm = residual,
            localHour = hour
        )
    }

    /** Tetens saturation-vapor-pressure relation, converted to a fixed column water capacity. */
    private fun saturationColumnMm(temperatureK: Double): Double {
        val celsius = temperatureK - 273.15
        val denominator = celsius + 243.5
        if (abs(denominator) < 1e-6) return 0.05
        val vaporPressurePa = 611.2 * exp(17.67 * celsius / denominator)
        val mixingRatio = 0.622 * vaporPressurePa / (PRESSURE_PA - 0.378 * vaporPressurePa)
        return mixingRatio * EFFECTIVE_AIR_COLUMN_KG_M2
    }
}
