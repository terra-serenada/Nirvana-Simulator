package org.terrachemica.app.core

import org.terrachemica.app.data.ArchaeanReference
import java.math.BigDecimal
import java.math.MathContext

/** A row-level comparison between formula-derived atom counts and the PDF ledger. */
data class StoichiometricDifference(
    val reservoir: ReservoirId,
    val elementSymbol: String,
    val formulaDerivedMolAtoms: BigDecimal,
    val sourceLedgerMolAtoms: BigDecimal,
    val relativeDifference: BigDecimal
)

data class ArchaeanStoichiometryReport(
    val valid: Boolean,
    val reservoirCount: Int,
    val elementCount: Int,
    val speciesCount: Int,
    val waterStoreCount: Int,
    val comparisonCount: Int,
    val relativeTolerance: BigDecimal,
    val maxRelativeDifference: BigDecimal,
    val differences: List<StoichiometricDifference>,
    val issues: List<String>
)

/**
 * Validates whether species + H2O store inventories reproduce the source's atom
 * ledgers. It does not infer reaction kinetics or modify any reference values.
 */
object ArchaeanStoichiometryAudit {
    private val relativeTolerance = BigDecimal("1e-9")
    private val mathContext = MathContext.DECIMAL128

    fun validate(): ArchaeanStoichiometryReport {
        val elementSymbols = ArchaeanReference.elements.map { it.symbol }.toSet()
        val reservoirs = ReservoirId.values().toSet()
        val issues = mutableListOf<String>()
        val differences = mutableListOf<StoichiometricDifference>()
        var speciesCount = 0
        var waterStoreCount = 0
        var comparisons = 0
        var maxRelativeDifference = BigDecimal.ZERO

        if (ArchaeanReference.stoichiometricSpeciesByReservoir.keys != reservoirs) {
            issues += "Daftar spesies tidak mencakup tepat enam reservoir"
        }
        if (ArchaeanReference.waterMoleculesByReservoir.keys != reservoirs) {
            issues += "Daftar store air tidak mencakup tepat enam reservoir"
        }
        if (ArchaeanReference.elementalInventoryByReservoir.keys != reservoirs) {
            issues += "Ledger sumber tidak mencakup tepat enam reservoir"
        }

        reservoirs.forEach { reservoir ->
            val derived = elementSymbols.associateWith { BigDecimal.ZERO }.toMutableMap()
            val sourceSpecies = ArchaeanReference.stoichiometricSpeciesByReservoir[reservoir].orEmpty()
            speciesCount += sourceSpecies.size

            sourceSpecies.forEach { species ->
                try {
                    val formulaAtoms = ChemicalFormulaParser.parse(species.formula)
                    val unknown = formulaAtoms.keys - elementSymbols
                    if (unknown.isNotEmpty()) {
                        issues += "${species.label}: unsur tidak terdaftar: ${unknown.joinToString()}"
                    }
                    val amount = BigDecimal(species.amountMol)
                    if (amount < BigDecimal.ZERO) issues += "${species.label}: inventaris negatif"
                    formulaAtoms.forEach { (element, atomCount) ->
                        if (element in derived) {
                            val contribution = amount.multiply(BigDecimal.valueOf(atomCount.toLong()))
                            derived[element] = derived.getValue(element).add(contribution)
                        }
                    }
                } catch (error: Exception) {
                    issues += "Formula ${species.label} (${species.formula}) tidak dapat diproses: ${error.message}"
                }
            }

            val waterStores = ArchaeanReference.waterMoleculesByReservoir[reservoir].orEmpty()
            waterStoreCount += waterStores.size
            waterStores.forEach { amountText ->
                val amount = BigDecimal(amountText)
                if (amount < BigDecimal.ZERO) issues += "$reservoir: store air negatif"
                derived["H"] = derived.getValue("H").add(amount.multiply(BigDecimal("2")))
                derived["O"] = derived.getValue("O").add(amount)
            }

            val sourceLedger = ArchaeanReference.elementalInventoryByReservoir[reservoir].orEmpty()
            elementSymbols.forEach elementLoop@ { element ->
                comparisons++
                val expectedText = sourceLedger[element]
                if (expectedText == null) {
                    issues += "$reservoir: ledger unsur $element tidak tersedia"
                    return@elementLoop
                }
                val expected = BigDecimal(expectedText)
                val actual = derived.getValue(element)
                val absoluteDifference = actual.subtract(expected).abs()
                val relativeDifference = if (expected.compareTo(BigDecimal.ZERO) == 0) {
                    if (absoluteDifference.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else BigDecimal.ONE
                } else {
                    absoluteDifference.divide(expected.abs(), mathContext)
                }
                if (relativeDifference > maxRelativeDifference) maxRelativeDifference = relativeDifference
                if (relativeDifference > relativeTolerance) {
                    differences += StoichiometricDifference(
                        reservoir, element, actual, expected, relativeDifference
                    )
                }
            }
        }

        return ArchaeanStoichiometryReport(
            valid = issues.isEmpty() && differences.isEmpty(),
            reservoirCount = reservoirs.size,
            elementCount = elementSymbols.size,
            speciesCount = speciesCount,
            waterStoreCount = waterStoreCount,
            comparisonCount = comparisons,
            relativeTolerance = relativeTolerance,
            maxRelativeDifference = maxRelativeDifference,
            differences = differences,
            issues = issues
        )
    }
}
