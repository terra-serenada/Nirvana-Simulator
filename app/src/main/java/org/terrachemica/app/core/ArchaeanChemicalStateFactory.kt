package org.terrachemica.app.core

import org.terrachemica.app.data.ArchaeanReference
import java.math.BigDecimal

/**
 * Builds a molecular/ionic working state from the static 3.8 Ga checkpoint.
 * Water sub-stores are aggregated by parent reservoir in this v0.3 reaction
 * kernel; the original sub-store detail remains untouched in the source asset.
 */
object ArchaeanChemicalStateFactory {
    private val sourceChargeByLabel = mapOf(
        "Cl⁻" to -1,
        "Na⁺" to 1,
        "Ca²⁺" to 2,
        "Mg²⁺" to 2,
        "K⁺" to 1,
        "HCO₃⁻" to -1,
        "Fe²⁺" to 2,
        "CO₃²⁻" to -2,
        "SO₄²⁻" to -2,
        "PO₄³⁻" to -3
    )

    fun create(): ChemicalSystemState {
        val definitions = linkedMapOf<String, ChemicalSpeciesDefinition>()
        val inventories = ReservoirId.values().associateWith { linkedMapOf<String, BigDecimal>() }.toMutableMap()

        ArchaeanReference.stoichiometricSpeciesByReservoir.forEach { (reservoir, species) ->
            species.forEach { source ->
                val id = speciesId(reservoir, source.formula)
                val phase = phaseFor(reservoir)
                definitions[id] = ChemicalSpeciesDefinition(
                    id = id,
                    displayName = source.label,
                    formula = source.formula,
                    formalCharge = sourceChargeByLabel[source.label] ?: 0,
                    phase = phase,
                    provenance = "Archaean of Arus v0.1 · static inventory checkpoint"
                )
                inventories.getValue(reservoir)[id] = BigDecimal(source.amountMol)
            }
        }

        // Preserve water atom counts while aggregating the per-layer/per-depth
        // H2O stores into one species pool per parent reservoir for v0.3.
        val waterIdByReservoir = mapOf(
            ReservoirId.ATMOSPHERIC to "ATMOSPHERIC_H2O",
            ReservoirId.OCEAN to "OCEAN_H2O",
            ReservoirId.HYDROTHERMAL_FLUID to "HYDROTHERMAL_FLUID_H2O"
        )
        waterIdByReservoir.forEach { (reservoir, id) ->
            val stores = ArchaeanReference.waterMoleculesByReservoir[reservoir].orEmpty()
            val total = stores.fold(BigDecimal.ZERO) { sum, amount -> sum.add(BigDecimal(amount)) }
            definitions[id] = ChemicalSpeciesDefinition(
                id = id,
                displayName = when (reservoir) {
                    ReservoirId.ATMOSPHERIC -> "H₂O vapor atmosfer"
                    ReservoirId.OCEAN -> "H₂O cair samudra"
                    else -> "H₂O fluida hidrotermal"
                },
                formula = "H2O",
                formalCharge = 0,
                phase = when (reservoir) {
                    ReservoirId.ATMOSPHERIC -> "gas"
                    ReservoirId.OCEAN -> "liquid"
                    else -> "liquid_pore_brine"
                },
                provenance = "Agregasi store H₂O dari Archaean of Arus v0.1; rincian sub-store tetap di sumber"
            )
            inventories.getValue(reservoir)[id] = total
        }

        // Explicit zero-seed species used by the reaction catalog. They are not
        // claimed to be measured/contained in the uploaded Archaean snapshot.
        val syntheticDefinitions = listOf(
            ChemicalSpeciesDefinition(
                id = "OCEAN_HPLUS", displayName = "Ion hidrogen", formula = "H",
                formalCharge = 1, phase = "aqueous",
                provenance = "Species tambahan untuk template reaksi; inventaris awal nol, bukan data sumber"
            ),
            ChemicalSpeciesDefinition(
                id = "OCEAN_CALCITE", displayName = "Kalsit", formula = "CaCO3",
                formalCharge = 0, phase = "solid",
                provenance = "Produk template presipitasi; inventaris awal nol, bukan data sumber"
            )
        )
        syntheticDefinitions.forEach { definitions[it.id] = it }
        inventories.getValue(ReservoirId.OCEAN)["OCEAN_HPLUS"] = BigDecimal.ZERO
        inventories.getValue(ReservoirId.OCEAN)["OCEAN_CALCITE"] = BigDecimal.ZERO

        return ChemicalSystemState(
            speciesDefinitions = definitions.toMap(),
            speciesMolByReservoir = inventories.mapValues { it.value.toMap() }
        )
    }

    fun validate(): ChemicalStateFactoryReport {
        val state = create()
        val sourceAudit = ArchaeanStoichiometryAudit.validate()
        val negative = state.speciesMolByReservoir.flatMap { (reservoir, amounts) ->
            amounts.filterValues { it < BigDecimal.ZERO }.keys.map { "$reservoir/$it" }
        }
        val undefined = state.speciesMolByReservoir.values.flatMap { it.keys }.filterNot { it in state.speciesDefinitions }
        val issues = buildList {
            if (!sourceAudit.valid) addAll(sourceAudit.issues)
            if (negative.isNotEmpty()) add("Inventaris negatif: ${negative.joinToString()}")
            if (undefined.isNotEmpty()) add("Definisi spesies hilang: ${undefined.joinToString()}")
            if (state.speciesMolByReservoir.keys != ReservoirId.values().toSet()) add("Enam reservoir belum lengkap")
        }
        return ChemicalStateFactoryReport(
            valid = issues.isEmpty(),
            reservoirCount = state.speciesMolByReservoir.size,
            sourceSpeciesCount = ArchaeanReference.stoichiometricSpeciesByReservoir.values.sumOf { it.size },
            waterStoreCount = ArchaeanReference.waterMoleculesByReservoir.values.sumOf { it.size },
            workingSpeciesCount = state.speciesDefinitions.size,
            sourceStoichiometryResidualMax = sourceAudit.maxRelativeDifference,
            issues = issues
        )
    }

    private fun speciesId(reservoir: ReservoirId, formula: String): String {
        val compact = formula.uppercase().filter { it.isLetterOrDigit() }
        return "${reservoir.name}_$compact"
    }

    private fun phaseFor(reservoir: ReservoirId): String = when (reservoir) {
        ReservoirId.ATMOSPHERIC -> "gas"
        ReservoirId.OCEAN -> "aqueous"
        ReservoirId.TERRESTRIAL -> "terrestrial_unspecified"
        ReservoirId.CRUST -> "solid"
        ReservoirId.HYDROTHERMAL_FLUID -> "aqueous"
        ReservoirId.MAGMA -> "melt_unspecified"
    }
}

data class ChemicalStateFactoryReport(
    val valid: Boolean,
    val reservoirCount: Int,
    val sourceSpeciesCount: Int,
    val waterStoreCount: Int,
    val workingSpeciesCount: Int,
    val sourceStoichiometryResidualMax: BigDecimal,
    val issues: List<String>
)
