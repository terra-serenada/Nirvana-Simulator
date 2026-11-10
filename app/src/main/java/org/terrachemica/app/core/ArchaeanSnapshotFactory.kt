package org.terrachemica.app.core

import org.terrachemica.app.data.ArchaeanReference
import java.math.BigDecimal

/**
 * Builds the static Archaean reference checkpoint from source-transcribed values.
 * It must only be attached to the Earth timeline at its stated 3.8 Ga lookback
 * anchor; it is not extrapolated backward to the Hadean or forward to modern Earth.
 */
object ArchaeanSnapshotFactory {
    fun createElementalState(): ElementalSystemState {
        val expectedElements = ArchaeanReference.elements.map { it.symbol }.toSet()
        val sourceReservoirs = ArchaeanReference.elementalInventoryByReservoir
        require(sourceReservoirs.keys == ElementalConservationEngine.requiredReservoirs) {
            "Dataset Archaean harus memuat tepat enam reservoir"
        }
        sourceReservoirs.forEach { (reservoir, entries) ->
            require(entries.keys == expectedElements) {
                "Ledger $reservoir harus memuat tepat 14 unsur"
            }
        }

        val state = expectedElements.associateWith { element ->
            sourceReservoirs.mapValues { (_, byElement) ->
                val value = byElement[element]
                    ?: error("Nilai $element tidak tersedia pada ledger reservoir")
                BigDecimal(value)
            }
        }
        return ElementalSystemState(state)
    }

    fun expectedTotals(state: ElementalSystemState): Map<String, BigDecimal> =
        state.molAtomsByElement.mapValues { (_, amounts) ->
            amounts.values.fold(BigDecimal.ZERO, BigDecimal::add)
        }

    fun validate(): ElementalConservationReport {
        val state = createElementalState()
        val report = ElementalConservationEngine.audit(state)
        check(report.valid) { report.issues.joinToString("; ") }
        check(report.elementCount == 14) { "Referensi seharusnya memuat 14 unsur" }
        check(report.reservoirCount == 6) { "Referensi seharusnya memuat enam reservoir" }
        check(report.amountEntryCount == 84) { "Referensi seharusnya memuat 84 nilai ledger" }
        return report
    }
}
