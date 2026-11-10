import org.terrachemica.app.core.ClockMode
import org.terrachemica.app.core.EarthEvolutionModel
import org.terrachemica.app.core.EvolutionClock
import org.terrachemica.app.core.ConservativeLedger
import org.terrachemica.app.core.ArchaeanSnapshotFactory
import org.terrachemica.app.core.ArchaeanStoichiometryAudit
import org.terrachemica.app.core.ChemicalFormulaParser
import org.terrachemica.app.core.ArchaeanChemicalStateFactory
import org.terrachemica.app.core.ReactionNetworkCatalog
import org.terrachemica.app.core.ChemicalReactionEngine
import org.terrachemica.app.core.ChemicalReaction
import org.terrachemica.app.core.ReactionTerm
import org.terrachemica.app.core.ReactionFamily
import org.terrachemica.app.core.EnergyAccountingStatus
import org.terrachemica.app.core.ElementTransfer
import org.terrachemica.app.core.ElementalConservationEngine
import org.terrachemica.app.core.ReservoirId
import org.terrachemica.app.core.SpeciesLedger
import org.terrachemica.app.core.SolarRadiationEngine
import org.terrachemica.app.core.SolarSpectrumData
import org.terrachemica.app.core.SolarRadiationReport
import org.terrachemica.app.core.SolarBandIrradiance
import org.terrachemica.app.core.AtmosphericRadiativeTransferEngine
import org.terrachemica.app.core.AtmosphericWeatherEngine
import org.terrachemica.app.core.WeatherForcing
import java.math.BigDecimal
import kotlin.math.abs

fun main() {
    val start = EarthEvolutionModel.snapshot(0.0)
    check(start.lookbackMa == 4540.0) { "Wrong starting lookback age" }
    check(start.eon == EarthEvolutionModel.Eon.HADEAN) { "Earth formation should be Hadean" }
    check(start.solarLuminosityFraction == null) { "Very young Sun should be marked outside the Gough approximation" }

    val archaean = EarthEvolutionModel.snapshot(740.0)
    check(archaeanLookback(archaean = archaean.lookbackMa))
    check(archaean.eon == EarthEvolutionModel.Eon.ARCHAEAN)
    check(archaean.nearestSpectrum.fileName == "Sun3.8Ga.txt")
    check(abs((archaean.solarLuminosityFraction ?: 0.0) - 0.75) < 0.01)
    check(archaean.dayLengthHours == 14.0)

    val modern = EarthEvolutionModel.snapshot(4540.0)
    check(modern.eon == EarthEvolutionModel.Eon.PHANEROZOIC)
    check(modern.nearestSpectrum.fileName == "SunModern.txt")
    check(abs((modern.solarLuminosityFraction ?: 0.0) - 1.0) < 1e-9)

    val autoJourney = EvolutionClock(initialMode = ClockMode.AUTO_JOURNEY, initialPlaying = true)
    autoJourney.advance(0L)
    val firstMilestone = autoJourney.advance(28_000_000_000L)
    check(abs(firstMilestone.ageSinceFormationMa - 140.0) < 1e-9) {
        "Auto Journey must reach the first milestone after one fixed 28 s segment"
    }
    check(firstMilestone.waitingAtMilestone)
    val stillHolding = autoJourney.advance(30_000_000_000L)
    check(abs(stillHolding.ageSinceFormationMa - 140.0) < 1e-9 && stillHolding.waitingAtMilestone)
    autoJourney.advance(32_000_000_000L)
    val nextSegment = autoJourney.advance(33_000_000_000L)
    val expectedAutoAge = 140.0 + (509.0 - 140.0) / 28.0
    check(abs(nextSegment.ageSinceFormationMa - expectedAutoAge) < 1e-9) {
        "Auto Journey segment should advance linearly after the milestone hold"
    }

    val daily = EvolutionClock(initialMode = ClockMode.SCIENTIFIC_DAILY)
    daily.advance(1_000_000_000L)
    val afterOneSecond = daily.advance(2_000_000_000L)
    val expectedMa = 1.0 / (365.2425 * 1_000_000.0)
    check(abs(afterOneSecond.ageSinceFormationMa - expectedMa) < 1e-12) {
        "Daily mode does not equal exactly one simulated day per real second"
    }

    val co2Ledger = SpeciesLedger(
        "CO2",
        mapOf(
            ReservoirId.ATMOSPHERIC to BigDecimal("1000"),
            ReservoirId.OCEAN to BigDecimal("250"),
            ReservoirId.TERRESTRIAL to BigDecimal("0"),
            ReservoirId.CRUST to BigDecimal("0"),
            ReservoirId.HYDROTHERMAL_FLUID to BigDecimal("0"),
            ReservoirId.MAGMA to BigDecimal("0")
        )
    )
    val (afterTransfer, audit) = ConservativeLedger.transfer(
        co2Ledger, ReservoirId.ATMOSPHERIC, ReservoirId.OCEAN, BigDecimal("125")
    )
    check(audit.conserved)
    check(afterTransfer.amountByReservoirMol[ReservoirId.ATMOSPHERIC] == BigDecimal("875"))
    check(afterTransfer.totalMol().compareTo(BigDecimal("1250")) == 0)
    val rejectedOverspend = runCatching {
        ConservativeLedger.transfer(afterTransfer, ReservoirId.ATMOSPHERIC, ReservoirId.OCEAN, BigDecimal("900"))
    }.isFailure
    check(rejectedOverspend) { "Ledger must reject a transfer larger than the available inventory" }

    val parsedApatite = ChemicalFormulaParser.parse("Ca5(PO4)3OH")
    check(parsedApatite == mapOf("Ca" to 5, "H" to 1, "O" to 13, "P" to 3))
    val parsedSerpentine = ChemicalFormulaParser.parse("Mg3Si2O5(OH)4")
    check(parsedSerpentine == mapOf("H" to 4, "Mg" to 3, "O" to 9, "Si" to 2))

    val archaeanState = ArchaeanSnapshotFactory.createElementalState()
    val snapshotReport = ArchaeanSnapshotFactory.validate()
    check(snapshotReport.valid)
    check(snapshotReport.elementCount == 14)
    check(snapshotReport.reservoirCount == 6)
    check(snapshotReport.amountEntryCount == 84)

    val stoichReport = ArchaeanStoichiometryAudit.validate()
    check(stoichReport.valid) {
        "Stoichiometry mismatch: ${stoichReport.issues.joinToString("; ")} ${stoichReport.differences}"
    }
    check(stoichReport.comparisonCount == 84)
    check(stoichReport.speciesCount == 45)
    check(stoichReport.waterStoreCount == 8)

    val expectedTotals = ArchaeanSnapshotFactory.expectedTotals(archaeanState)
    val (movedState, elementAudit) = ElementalConservationEngine.transfer(
        archaeanState,
        ElementTransfer(
            elementSymbol = "H",
            from = ReservoirId.ATMOSPHERIC,
            to = ReservoirId.OCEAN,
            amountMolAtoms = BigDecimal("1000000000"),
            processLabel = "uji-konservasi"
        )
    )
    check(elementAudit.conserved)
    check(ElementalConservationEngine.audit(movedState, expectedTotals).valid)

    val invalidBatch = listOf(
        ElementTransfer("H", ReservoirId.ATMOSPHERIC, ReservoirId.OCEAN, BigDecimal("1"), "uji-valid"),
        ElementTransfer("H", ReservoirId.TERRESTRIAL, ReservoirId.OCEAN, BigDecimal("1"), "uji-gagal-karena-stok-nol")
    )
    val batchRejected = runCatching {
        ElementalConservationEngine.transferBatch(state = archaeanState, transfers = invalidBatch)
    }.isFailure
    check(batchRejected) { "Batch invalid must be rejected atomically" }
    check(archaeanState == ArchaeanSnapshotFactory.createElementalState())


    val chemicalFactoryReport = ArchaeanChemicalStateFactory.validate()
    check(chemicalFactoryReport.valid) {
        "Chemical state invalid: ${chemicalFactoryReport.issues.joinToString("; ")}"
    }
    check(chemicalFactoryReport.sourceSpeciesCount == 45)
    check(chemicalFactoryReport.waterStoreCount == 8)
    val chemicalState = ArchaeanChemicalStateFactory.create()
    val reactionCatalogReport = ReactionNetworkCatalog.validateAll(chemicalState)
    check(reactionCatalogReport.valid) {
        "Reaction catalog invalid: ${reactionCatalogReport.reports.flatMap { it.issues }}"
    }
    check(reactionCatalogReport.reactionCount == 4)
    check(reactionCatalogReport.balancedCount == 4)
    check(reactionCatalogReport.withStandardEnthalpyCount == 1)
    check(reactionCatalogReport.withKineticLawCount == 0)

    val methanation = ReactionNetworkCatalog.reactions.first { it.id == "ATMOSPHERIC_CO2_METHANATION" }
    val extent = BigDecimal("1000000000000")
    val reactionResult = ChemicalReactionEngine.execute(chemicalState, methanation, extent)
    check(reactionResult.balance.valid)
    check(reactionResult.chargeResidualMol.compareTo(BigDecimal.ZERO) == 0)
    check(reactionResult.elementalResidualBySymbol.values.all { it.compareTo(BigDecimal.ZERO) == 0 })
    check(reactionResult.energy.status == EnergyAccountingStatus.CLOSED)
    check(reactionResult.energy.reactionEnthalpyChangeKJ?.compareTo(BigDecimal("-165012000000000")) == 0)
    check(reactionResult.energy.thermalEnergyChangeKJ?.compareTo(BigDecimal("165012000000000")) == 0)
    check(reactionResult.energy.netEnergyResidualKJ?.compareTo(BigDecimal.ZERO) == 0)
    check(reactionResult.state.speciesMolByReservoir.getValue(ReservoirId.ATMOSPHERIC)
        .getValue("ATMOSPHERIC_CO2").compareTo(chemicalState.speciesMolByReservoir.getValue(ReservoirId.ATMOSPHERIC).getValue("ATMOSPHERIC_CO2").subtract(extent)) == 0)

    val carbonateHydration = ReactionNetworkCatalog.reactions.first { it.id == "OCEAN_CO2_HYDRATION_DISSOCIATION" }
    val missingEnergyRejected = runCatching {
        ChemicalReactionEngine.execute(chemicalState, carbonateHydration, BigDecimal.ONE)
    }.isFailure
    check(missingEnergyRejected) { "Reaction without enthalpy must not run with complete energy accounting" }
    val incompleteEnergyResult = ChemicalReactionEngine.execute(
        chemicalState, carbonateHydration, BigDecimal.ONE, requireCompleteEnergyAccounting = false
    )
    check(incompleteEnergyResult.energy.status == EnergyAccountingStatus.MISSING_REACTION_ENTHALPY)
    check(incompleteEnergyResult.energy.netEnergyResidualKJ == null)
    check(incompleteEnergyResult.chargeResidualMol.compareTo(BigDecimal.ZERO) == 0)

    val deliberatelyUnbalanced = ChemicalReaction(
        id = "TEST_UNBALANCED",
        label = "Reaksi uji tidak seimbang",
        reservoir = ReservoirId.ATMOSPHERIC,
        reactants = listOf(ReactionTerm("ATMOSPHERIC_H2", BigDecimal.ONE)),
        products = listOf(ReactionTerm("ATMOSPHERIC_CH4", BigDecimal.ONE)),
        standardReactionEnthalpyKJ = null,
        referenceTemperatureK = null,
        referencePressureBar = null,
        family = ReactionFamily.OTHER,
        source = "test",
        scientificLimit = "test"
    )
    check(!ChemicalReactionEngine.validate(deliberatelyUnbalanced, chemicalState.speciesDefinitions).valid)

    val oversizedExtentRejected = runCatching {
        ChemicalReactionEngine.execute(chemicalState, methanation, BigDecimal("1e30"))
    }.isFailure
    check(oversizedExtentRejected)
    check(chemicalState.speciesMolByReservoir.getValue(ReservoirId.ATMOSPHERIC)
        .getValue("ATMOSPHERIC_CO2").compareTo(BigDecimal("1.18023136e19")) == 0) {
        "Reaction transaction must leave original source checkpoint immutable"
    }

    // v0.4 spectral parser, boundary-aware integration, temporal interpolation and TOA geometry.
    val syntheticSpectrumText = """
        # VPL Past Solar Spectrum test
        # Wavelength (nm)    Flux (erg/s/cm2/nm)
        5.0000000000e-01 1.0000000000e+03
        1.0000000000e+00 2.0000000000e+03
        1.5000000000e+00 3.0000000000e+03
    """.trimIndent()
    val parsedSpectrum = SolarRadiationEngine.parseVplSpectrum("synthetic.txt", 1.0, syntheticSpectrumText)
    check(abs(parsedSpectrum.irradianceWm2Nm[0] - 1.0) < 1e-12)
    check(abs(parsedSpectrum.irradianceWm2Nm[1] - 2.0) < 1e-12)
    val edgeIntegral = SolarRadiationEngine.integrateBand(parsedSpectrum, 0.75, 1.25)
    check(abs(edgeIntegral - 1.0) < 1e-12) { "Spectral integral must interpolate requested boundaries" }

    val bandBoundaries = doubleArrayOf(0.5, 10.0, 121.0, 200.0, 280.0, 315.0, 400.0, 700.0, 2397.51)
    val olderSpectrum = SolarSpectrumData(
        "older.txt", 2.0, bandBoundaries, DoubleArray(bandBoundaries.size) { 4.0 }
    )
    val newerGrid = bandBoundaries.flatMapIndexed { index, value ->
        if (index < bandBoundaries.lastIndex) listOf(value, (value + bandBoundaries[index + 1]) / 2.0) else listOf(value)
    }.sorted().distinct().toDoubleArray()
    val newerSpectrum = SolarSpectrumData(
        "newer.txt", 0.0, newerGrid, DoubleArray(newerGrid.size) { 1.0 }
    )
    val middleSpectrum = SolarRadiationEngine.interpolateInTime(olderSpectrum, newerSpectrum, 1.0)
    check(middleSpectrum.irradianceWm2Nm.all { abs(it - 2.0) < 1e-12 }) {
        "Log-flux midpoint interpolation should be the geometric mean"
    }
    val radiationDiagnostic = SolarRadiationEngine.calculateAtTopOfAtmosphere(middleSpectrum, bondAlbedo = 0.3)
    val expectedBandWidth = SolarRadiationEngine.DATA_MAX_WAVELENGTH_NM - SolarRadiationEngine.DATA_MIN_WAVELENGTH_NM
    check(abs(radiationDiagnostic.coveredBandIrradianceWm2 - expectedBandWidth * 2.0) < 1e-8)
    check(abs(radiationDiagnostic.globalMeanIncomingWm2 - radiationDiagnostic.coveredBandIrradianceWm2 / 4.0) < 1e-12)
    check(abs(radiationDiagnostic.absorbedGlobalMeanWm2!! - radiationDiagnostic.globalMeanIncomingWm2 * 0.7) < 1e-12)
    val noAlbedoDiagnostic = SolarRadiationEngine.calculateAtTopOfAtmosphere(middleSpectrum)
    check(noAlbedoDiagnostic.absorbedGlobalMeanWm2 == null && noAlbedoDiagnostic.reflectedGlobalMeanWm2 == null)
    val extrapolationRejected = runCatching {
        SolarRadiationEngine.interpolateInTime(olderSpectrum, newerSpectrum, 2.1)
    }.isFailure
    check(extrapolationRejected) { "Spectral interpolation must not extrapolate beyond source anchors" }
    check(abs(SolarRadiationEngine.bands.sumOf { it.maximumNm - it.minimumNm } - expectedBandWidth) < 1e-9)
    val pdfExpectedBands = listOf(
        0.005813707, 0.035405974, 0.148874598, 4.279050209,
        11.682507388, 59.617487306, 395.947185897, 527.954651519
    )
    val pdfExpectedTotal = pdfExpectedBands.sum()
    check(abs(pdfExpectedTotal - 999.670976598) < 1e-9)
    val pdfReferenceReport = SolarRadiationReport(
        sourceDescription = "PDF reference table",
        lookbackGa = 3.8,
        coveredBandIrradianceWm2 = pdfExpectedTotal,
        globalMeanIncomingWm2 = pdfExpectedTotal / 4.0,
        bands = pdfExpectedBands.mapIndexed { index, value -> SolarBandIrradiance(SolarRadiationEngine.bands[index], value) },
        bondAlbedo = null,
        reflectedGlobalMeanWm2 = null,
        absorbedGlobalMeanWm2 = null
    )
    check(SolarRadiationEngine.validateArchaeanPdfBenchmark(pdfReferenceReport).valid)
    check(abs(pdfReferenceReport.globalMeanIncomingWm2 - 249.917744150) < 1e-9)

    // v0.5 absorption-only spectral transfer and one-layer grey longwave equilibrium.
    val effectivePreset = AtmosphericRadiativeTransferEngine.buildModernEffectivePreset(
        SolarRadiationEngine.bands.map { it.id }.toSet()
    )
    val shortwaveBudget = AtmosphericRadiativeTransferEngine.calculateShortwave(
        pdfReferenceReport,
        effectivePreset.shortwaveModel
    )
    check(AtmosphericRadiativeTransferEngine.validateEnergyClosure(shortwaveBudget)) {
        "Shortwave energy residual: ${shortwaveBudget.energyResidualWm2}"
    }
    val shortwaveIncoming = pdfReferenceReport.globalMeanIncomingWm2
    check(abs(shortwaveBudget.atmosphereAbsorbedWm2 / shortwaveIncoming - 0.23) < 1e-10)
    check(abs(shortwaveBudget.surfaceAbsorbedWm2 / shortwaveIncoming - 0.48) < 1e-10)
    check(abs(shortwaveBudget.reflectedToSpaceWm2 / shortwaveIncoming - 0.29) < 1e-10)
    check(shortwaveBudget.bandBudgets.size == 8)
    check(shortwaveBudget.bandBudgets.all { abs(it.energyResidualWm2) < 1e-10 })
    val threeLayerOptics = effectivePreset.shortwaveModel.copy(
        modelId = "THREE_LAYER_SPLIT_CHECK",
        bandOptics = effectivePreset.shortwaveModel.bandOptics.mapValues { (bandId, _) ->
            val tau = -effectivePreset.shortwaveModel.effectiveCosineZenith *
                kotlin.math.ln(effectivePreset.downTransmittance)
            org.terrachemica.app.core.BandOpticalDepths(
                bandId,
                listOf("upper" to tau / 3.0, "middle" to tau / 3.0, "lower" to tau / 3.0)
            )
        }
    )
    val threeLayerBudget = AtmosphericRadiativeTransferEngine.calculateShortwave(pdfReferenceReport, threeLayerOptics)
    check(AtmosphericRadiativeTransferEngine.validateEnergyClosure(threeLayerBudget))
    check(abs(threeLayerBudget.atmosphereAbsorbedWm2 / shortwaveIncoming - 0.23) < 1e-10)

    val modernGrey = AtmosphericRadiativeTransferEngine.calculateGreyLongwaveEquilibrium(
        solarAgeGa = EarthEvolutionModel.SUN_AGE_GA,
        luminosityFraction = 1.0,
        preset = effectivePreset
    )
    check(AtmosphericRadiativeTransferEngine.validateEnergyClosure(modernGrey)) {
        "Grey longwave residuals: surface=${modernGrey.surfaceEnergyResidualWm2}, atmosphere=${modernGrey.atmosphereEnergyResidualWm2}, TOA=${modernGrey.topOfAtmosphereEnergyResidualWm2}"
    }
    check(abs(modernGrey.surfaceTemperatureK - 288.15) < 1e-8) {
        "Modern calibration should reproduce its 288.15 K target"
    }
    check(abs(modernGrey.outgoingLongwaveToSpaceWm2 - modernGrey.globalMeanIncomingSolarWm2 * 0.71) < 1e-8)
    check(abs(modernGrey.globalMeanIncomingSolarWm2 - 340.25) < 1e-10)

    val archaeanGrey = AtmosphericRadiativeTransferEngine.calculateGreyLongwaveEquilibrium(
        solarAgeGa = archaean.sunAgeGa,
        luminosityFraction = archaean.solarLuminosityFraction!!,
        preset = effectivePreset
    )
    check(AtmosphericRadiativeTransferEngine.validateEnergyClosure(archaeanGrey))
    check(archaeanGrey.surfaceTemperatureK < modernGrey.surfaceTemperatureK)
    val weatherEngine = AtmosphericWeatherEngine()
    val weatherForcing = WeatherForcing(1.0, modernGrey.surfaceTemperatureK)
    var weatherState = weatherEngine.advance(0.0, weatherForcing)
    repeat(5 * 24) {
        weatherState = weatherEngine.advance(3600.0, weatherForcing)
    }
    check(weatherState.accumulatedRainMm > 0.0) { "Weather column should produce precipitation from condensation" }
    check(weatherState.lightningEventCount > 0L) { "Moist convective cloud should accumulate electrical charge and flash" }
    check(abs(weatherState.waterBalanceResidualMm) < 1e-6) {
        "Water-cycle budget did not close: ${weatherState.waterBalanceResidualMm} mm"
    }
    check(weatherState.precipitationRateMmPerHour >= 0.0 && weatherState.cloudFraction in 0.0..1.0)
    val archaeanWeatherEngine = AtmosphericWeatherEngine()
    val archaeanWeatherForcing = WeatherForcing(
        archaean.solarLuminosityFraction,
        archaeanGrey.surfaceTemperatureK
    )
    var archaeanWeatherState = archaeanWeatherEngine.advance(0.0, archaeanWeatherForcing)
    repeat(5 * 24) {
        archaeanWeatherState = archaeanWeatherEngine.advance(3600.0, archaeanWeatherForcing)
    }
    check(archaeanWeatherState.accumulatedRainMm > 0.0)
    check(archaeanWeatherState.lightningEventCount > 0L)
    check(abs(archaeanWeatherState.waterBalanceResidualMm) < 1e-6)

    val noSolarEvolutionForYoungSun = EarthEvolutionModel.goughLuminosityFraction(start.sunAgeGa) == null
    check(noSolarEvolutionForYoungSun) { "Young-Sun luminosity gap must stay explicit" }

    println("AUTO JOURNEY CHECK PASSED: fixed-duration milestone travel and holds")
    println("SOLAR SPECTRUM CHECK PASSED: cgs→SI conversion; boundary interpolation; log-flux temporal interpolation; no extrapolation")
    println("TOA RADIATION CHECK PASSED: ${radiationDiagnostic.bands.size} wavelength bands; global mean = incident / 4; albedo optional")
    println("SHORTWAVE TRANSFER CHECK PASSED: ${shortwaveBudget.bandBudgets.size} bands; 1-layer and 3-layer paths; effective split atmosphere/surface/reflected = 23/48/29%; residual=${shortwaveBudget.energyResidualWm2} W/m²")
    println("GREY LONGWAVE CHECK PASSED: modern T_surface=${modernGrey.surfaceTemperatureK} K; T_atmosphere,eff=${modernGrey.effectiveAtmosphereTemperatureK} K; TOA residual=${modernGrey.topOfAtmosphereEnergyResidualWm2} W/m²")
    println("WEATHER COLUMN CHECK PASSED: modern rain=${weatherState.accumulatedRainMm} mm / lightning=${weatherState.lightningEventCount}; Archaean rain=${archaeanWeatherState.accumulatedRainMm} mm / lightning=${archaeanWeatherState.lightningEventCount}; water residuals=${weatherState.waterBalanceResidualMm}, ${archaeanWeatherState.waterBalanceResidualMm} mm")
    println("ARCHAEAN PDF BENCHMARK READY: 3.8 Ga integrated spectrum target = 999.670976598 W/m²; runtime file comparison uses per-band tolerances")

    println("REACTION ENGINE CHECK PASSED: ${reactionCatalogReport.balancedCount}/${reactionCatalogReport.reactionCount} balanced; ${reactionCatalogReport.withStandardEnthalpyCount} with sourced standard enthalpy; no automatic kinetics")
    println("REACTION EXECUTION CHECK PASSED: extent=$extent mol; elemental + charge residuals zero; energy residual=${reactionResult.energy.netEnergyResidualKJ} kJ")
    println("REFERENCE LEDGER CHECK PASSED: ${snapshotReport.elementCount} elements x ${snapshotReport.reservoirCount} reservoirs = ${snapshotReport.amountEntryCount} entries")
    println("ELEMENT CONSERVATION CHECK PASSED: ${elementAudit.elementSymbol} transfer conserves total atom moles")
    println("STOICHIOMETRY CHECK PASSED: ${stoichReport.speciesCount} species + ${stoichReport.waterStoreCount} water stores reconcile with ${stoichReport.comparisonCount} ledger entries")
    println("Maximum relative source-rounding residual: ${stoichReport.maxRelativeDifference}")
    println("CORE CHECK PASSED")
    println("At 3.8 Ga: L/L0=${archaean.solarLuminosityFraction}, spectrum=${archaean.nearestSpectrum.fileName}, day=${archaean.dayLengthHours} h")
    println("At present: L/L0=${modern.solarLuminosityFraction}, spectrum=${modern.nearestSpectrum.fileName}")
    println("One real second in Scientific Daily = one simulated day")
}

private fun archaeanLookback(archaean: Double): Boolean = abs(archaean - 3800.0) < 1e-8
