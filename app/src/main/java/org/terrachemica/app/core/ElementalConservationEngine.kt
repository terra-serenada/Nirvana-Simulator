package org.terrachemica.app.core

import java.math.BigDecimal

/**
 * Atom-mole ledger across the six Terra Chemica reservoirs.
 * Values are mol of atoms for one element (not mol of molecules/species).
 */
data class ElementalSystemState(
    val molAtomsByElement: Map<String, Map<ReservoirId, BigDecimal>>
) {
    fun totalMolAtoms(element: String): BigDecimal =
        molAtomsByElement[element]?.values?.fold(BigDecimal.ZERO, BigDecimal::add)
            ?: BigDecimal.ZERO
}

data class ElementTransfer(
    val elementSymbol: String,
    val from: ReservoirId,
    val to: ReservoirId,
    val amountMolAtoms: BigDecimal,
    val processLabel: String
)

data class ElementTransferAudit(
    val elementSymbol: String,
    val processLabel: String,
    val from: ReservoirId,
    val to: ReservoirId,
    val amountMolAtoms: BigDecimal,
    val totalBeforeMolAtoms: BigDecimal,
    val totalAfterMolAtoms: BigDecimal,
    val conserved: Boolean
)

data class ElementalConservationReport(
    val valid: Boolean,
    val elementCount: Int,
    val reservoirCount: Int,
    val amountEntryCount: Int,
    val issues: List<String>,
    val totalMolAtomsByElement: Map<String, BigDecimal>
)

data class TransferBatchResult(
    val state: ElementalSystemState,
    val audits: List<ElementTransferAudit>,
    val finalReport: ElementalConservationReport
)

/**
 * Conservative bookkeeping kernel. It can transfer atoms already represented in
 * the ledger, but it does not define chemical reactions, rates, or physical flows.
 * Those process kernels must be independently sourced and validated.
 */
object ElementalConservationEngine {
    val requiredReservoirs: Set<ReservoirId> = ReservoirId.values().toSet()

    fun audit(
        state: ElementalSystemState,
        expectedTotals: Map<String, BigDecimal>? = null
    ): ElementalConservationReport {
        val issues = mutableListOf<String>()
        if (state.molAtomsByElement.isEmpty()) issues += "Ledger unsur kosong"

        var entries = 0
        val totals = linkedMapOf<String, BigDecimal>()
        state.molAtomsByElement.toSortedMap().forEach { (element, byReservoir) ->
            if (element.isBlank()) issues += "Simbol unsur kosong"
            val missing = requiredReservoirs - byReservoir.keys
            val extra = byReservoir.keys - requiredReservoirs
            if (missing.isNotEmpty()) issues += "$element: reservoir belum lengkap: ${missing.joinToString()}"
            if (extra.isNotEmpty()) issues += "$element: reservoir tidak dikenal: ${extra.joinToString()}"

            var total = BigDecimal.ZERO
            requiredReservoirs.forEach { reservoir ->
                val amount = byReservoir[reservoir]
                if (amount != null) {
                    entries++
                    if (amount < BigDecimal.ZERO) issues += "$element di $reservoir bernilai negatif"
                    total = total.add(amount)
                }
            }
            totals[element] = total
        }

        expectedTotals?.forEach { (element, expected) ->
            val actual = totals[element]
            if (actual == null) {
                issues += "$element tidak ditemukan pada ledger saat ini"
            } else if (actual.compareTo(expected) != 0) {
                issues += "$element tidak konservatif: aktual=$actual, referensi=$expected"
            }
        }

        return ElementalConservationReport(
            valid = issues.isEmpty(),
            elementCount = state.molAtomsByElement.size,
            reservoirCount = requiredReservoirs.size,
            amountEntryCount = entries,
            issues = issues,
            totalMolAtomsByElement = totals
        )
    }

    fun transfer(
        state: ElementalSystemState,
        transfer: ElementTransfer
    ): Pair<ElementalSystemState, ElementTransferAudit> {
        require(transfer.elementSymbol in state.molAtomsByElement) {
            "Unsur ${transfer.elementSymbol} tidak ada di ledger"
        }
        require(transfer.from != transfer.to) { "Reservoir asal dan tujuan harus berbeda" }
        require(transfer.amountMolAtoms > BigDecimal.ZERO) { "Jumlah transfer harus positif" }
        require(transfer.processLabel.isNotBlank()) { "Label proses harus diisi" }

        val byReservoir = state.molAtomsByElement.getValue(transfer.elementSymbol)
        val available = byReservoir[transfer.from]
            ?: error("Reservoir ${transfer.from} belum tercatat untuk ${transfer.elementSymbol}")
        require(available >= transfer.amountMolAtoms) {
            "Inventaris ${transfer.elementSymbol} di ${transfer.from} tidak mencukupi"
        }

        val before = state.totalMolAtoms(transfer.elementSymbol)
        val updatedReservoirs = byReservoir.toMutableMap()
        updatedReservoirs[transfer.from] = available.subtract(transfer.amountMolAtoms)
        updatedReservoirs[transfer.to] = (updatedReservoirs[transfer.to] ?: BigDecimal.ZERO)
            .add(transfer.amountMolAtoms)

        val updatedElements = state.molAtomsByElement.toMutableMap()
        updatedElements[transfer.elementSymbol] = updatedReservoirs.toMap()
        val updatedState = ElementalSystemState(updatedElements.toMap())
        val after = updatedState.totalMolAtoms(transfer.elementSymbol)
        val audit = ElementTransferAudit(
            elementSymbol = transfer.elementSymbol,
            processLabel = transfer.processLabel,
            from = transfer.from,
            to = transfer.to,
            amountMolAtoms = transfer.amountMolAtoms,
            totalBeforeMolAtoms = before,
            totalAfterMolAtoms = after,
            conserved = before.compareTo(after) == 0
        )
        check(audit.conserved) { "Pelanggaran konservasi unsur ${transfer.elementSymbol}" }
        return updatedState to audit
    }

    /**
     * Applies a batch to immutable copies. If any transfer is invalid, no updated
     * state is returned and the caller's original state remains unchanged.
     */
    fun transferBatch(
        state: ElementalSystemState,
        transfers: List<ElementTransfer>,
        expectedTotals: Map<String, BigDecimal>? = null
    ): TransferBatchResult {
        val initialReport = audit(state, expectedTotals)
        require(initialReport.valid) {
            "Ledger awal tidak valid: ${initialReport.issues.joinToString("; ")}"
        }

        var candidate = state
        val audits = mutableListOf<ElementTransferAudit>()
        transfers.forEach { transfer ->
            val (next, audit) = transfer(candidate, transfer)
            candidate = next
            audits += audit
        }

        val finalReport = audit(candidate, expectedTotals ?: initialReport.totalMolAtomsByElement)
        check(finalReport.valid) {
            "Audit akhir gagal: ${finalReport.issues.joinToString("; ")}"
        }
        return TransferBatchResult(candidate, audits, finalReport)
    }
}
