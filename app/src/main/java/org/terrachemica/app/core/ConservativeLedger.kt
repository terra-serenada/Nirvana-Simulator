package org.terrachemica.app.core

import java.math.BigDecimal

/** Six system reservoirs used by Archaean of Arus v0.1. */
enum class ReservoirId {
    ATMOSPHERIC,
    OCEAN,
    TERRESTRIAL,
    CRUST,
    HYDROTHERMAL_FLUID,
    MAGMA
}

data class SpeciesLedger(
    val speciesId: String,
    val amountByReservoirMol: Map<ReservoirId, BigDecimal>
) {
    fun totalMol(): BigDecimal = amountByReservoirMol.values.fold(BigDecimal.ZERO, BigDecimal::add)
}

data class TransferAudit(
    val speciesId: String,
    val from: ReservoirId,
    val to: ReservoirId,
    val amountMol: BigDecimal,
    val totalBeforeMol: BigDecimal,
    val totalAfterMol: BigDecimal,
    val conserved: Boolean
)

/**
 * Exact, conservative movement of an already-defined species between reservoirs.
 * This deliberately does not create chemical reactions or transport rates; those
 * require separately sourced physical/chemical process kernels.
 */
object ConservativeLedger {
    fun transfer(
        ledger: SpeciesLedger,
        from: ReservoirId,
        to: ReservoirId,
        amountMol: BigDecimal
    ): Pair<SpeciesLedger, TransferAudit> {
        require(from != to) { "Reservoir asal dan tujuan harus berbeda" }
        require(amountMol > BigDecimal.ZERO) { "Jumlah transfer harus positif" }

        val available = ledger.amountByReservoirMol[from] ?: BigDecimal.ZERO
        require(available >= amountMol) { "Inventaris ${ledger.speciesId} di $from tidak mencukupi" }

        val before = ledger.totalMol()
        val updated = ledger.amountByReservoirMol.toMutableMap()
        updated[from] = available.subtract(amountMol)
        updated[to] = (updated[to] ?: BigDecimal.ZERO).add(amountMol)
        val result = SpeciesLedger(ledger.speciesId, updated)
        val after = result.totalMol()
        val audit = TransferAudit(
            speciesId = ledger.speciesId,
            from = from,
            to = to,
            amountMol = amountMol,
            totalBeforeMol = before,
            totalAfterMol = after,
            conserved = before.compareTo(after) == 0
        )
        check(audit.conserved) { "Pelanggaran konservasi saat transfer ${ledger.speciesId}" }
        return result to audit
    }
}
