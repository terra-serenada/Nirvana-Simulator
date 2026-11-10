package org.terrachemica.app.core

import java.math.BigDecimal

/** A chemically identified species. Charge is kept separately from the formula. */
data class ChemicalSpeciesDefinition(
    val id: String,
    val displayName: String,
    val formula: String,
    val formalCharge: Int = 0,
    val phase: String,
    val provenance: String
)

data class ReactionTerm(
    val speciesId: String,
    val coefficient: BigDecimal
)

enum class ReactionFamily { REDOX, ACID_BASE, PRECIPITATION, OTHER }

data class ChemicalReaction(
    val id: String,
    val label: String,
    val reservoir: ReservoirId,
    val reactants: List<ReactionTerm>,
    val products: List<ReactionTerm>,
    /** ΔrH° in kJ per reaction exactly as written; null means not parameterized. */
    val standardReactionEnthalpyKJ: BigDecimal?,
    val referenceTemperatureK: BigDecimal?,
    val referencePressureBar: BigDecimal?,
    val family: ReactionFamily,
    val source: String,
    /** Null means no kinetic law is currently supported. */
    val kineticsModelId: String? = null,
    val scientificLimit: String
)

data class ReactionBalanceReport(
    val reactionId: String,
    val valid: Boolean,
    val elementResidualBySymbol: Map<String, BigDecimal>,
    val chargeResidual: BigDecimal,
    val issues: List<String>
)

data class ChemicalEnergyState(
    /** Change in chemical enthalpy relative to the checkpoint baseline. */
    val reactionEnthalpyChangeKJByReservoir: Map<ReservoirId, BigDecimal>,
    /** Heat booked to the local thermal pool; not a temperature prediction. */
    val thermalEnergyChangeKJByReservoir: Map<ReservoirId, BigDecimal>
) {
    companion object {
        fun zero(): ChemicalEnergyState = ChemicalEnergyState(
            ReservoirId.values().associateWith { BigDecimal.ZERO },
            ReservoirId.values().associateWith { BigDecimal.ZERO }
        )
    }
}

data class ChemicalSystemState(
    val speciesDefinitions: Map<String, ChemicalSpeciesDefinition>,
    val speciesMolByReservoir: Map<ReservoirId, Map<String, BigDecimal>>,
    val energy: ChemicalEnergyState = ChemicalEnergyState.zero()
) {
    fun elementalTotals(): Map<String, BigDecimal> {
        val totals = linkedMapOf<String, BigDecimal>()
        speciesMolByReservoir.forEach { (_, amounts) ->
            amounts.forEach { (speciesId, amountMol) ->
                require(amountMol >= BigDecimal.ZERO) { "Inventaris $speciesId negatif" }
                val definition = speciesDefinitions[speciesId]
                    ?: error("Definisi spesies $speciesId tidak ditemukan")
                ChemicalFormulaParser.parse(definition.formula).forEach { (element, atomsPerMolecule) ->
                    totals[element] = (totals[element] ?: BigDecimal.ZERO).add(
                        amountMol.multiply(BigDecimal.valueOf(atomsPerMolecule.toLong()))
                    )
                }
            }
        }
        return totals.toSortedMap()
    }

    fun elementalTotalsByReservoir(reservoir: ReservoirId): Map<String, BigDecimal> {
        val totals = linkedMapOf<String, BigDecimal>()
        speciesMolByReservoir[reservoir].orEmpty().forEach { (speciesId, amountMol) ->
            require(amountMol >= BigDecimal.ZERO) { "Inventaris $speciesId negatif di $reservoir" }
            val definition = speciesDefinitions[speciesId]
                ?: error("Definisi spesies $speciesId tidak ditemukan")
            ChemicalFormulaParser.parse(definition.formula).forEach { (element, atomsPerMolecule) ->
                totals[element] = (totals[element] ?: BigDecimal.ZERO).add(
                    amountMol.multiply(BigDecimal.valueOf(atomsPerMolecule.toLong()))
                )
            }
        }
        return totals.toSortedMap()
    }

    fun chargeMolByReservoir(): Map<ReservoirId, BigDecimal> = ReservoirId.values().associateWith { reservoir ->
        speciesMolByReservoir[reservoir].orEmpty().entries.fold(BigDecimal.ZERO) { total, (id, amount) ->
            val charge = speciesDefinitions[id]?.formalCharge
                ?: error("Definisi spesies $id tidak ditemukan")
            total.add(amount.multiply(BigDecimal.valueOf(charge.toLong())))
        }
    }
}

enum class EnergyAccountingStatus { CLOSED, MISSING_REACTION_ENTHALPY }

data class ReactionEnergyAudit(
    val status: EnergyAccountingStatus,
    val reactionEnthalpyChangeKJ: BigDecimal?,
    val thermalEnergyChangeKJ: BigDecimal?,
    val netEnergyResidualKJ: BigDecimal?,
    val assumption: String
)

data class ReactionExecutionResult(
    val state: ChemicalSystemState,
    val reactionId: String,
    val extentMol: BigDecimal,
    val speciesDeltaMol: Map<String, BigDecimal>,
    val balance: ReactionBalanceReport,
    val chargeResidualMol: BigDecimal,
    val elementalResidualBySymbol: Map<String, BigDecimal>,
    val energy: ReactionEnergyAudit
)

/**
 * Stoichiometric reaction execution. This engine deliberately has no rate law.
 * A caller must supply a positive reaction extent. It never infers a rate from
 * inventory alone. All updates are immutable/transactional and exact-decimal.
 */
object ChemicalReactionEngine {
    const val ADIABATIC_BOOKKEEPING_ASSUMPTION =
        "Panas reaksi seluruhnya dibukukan ke perubahan energi termal reservoir yang sama; " +
            "tidak ada kerja, radiasi, konduksi, atau perpindahan panas pada transaksi ini. " +
            "Nilai adalah perubahan energi relatif, bukan energi internal absolut atau suhu."

    fun validate(
        reaction: ChemicalReaction,
        definitions: Map<String, ChemicalSpeciesDefinition>,
        allowedElements: Set<String>? = null
    ): ReactionBalanceReport {
        val issues = mutableListOf<String>()
        if (reaction.id.isBlank()) issues += "ID reaksi kosong"
        if (reaction.label.isBlank()) issues += "Nama reaksi kosong"
        if (reaction.reactants.isEmpty()) issues += "Sisi reaktan kosong"
        if (reaction.products.isEmpty()) issues += "Sisi produk kosong"

        fun validateSide(sideName: String, terms: List<ReactionTerm>) {
            val duplicates = terms.groupingBy { it.speciesId }.eachCount().filterValues { it > 1 }.keys
            if (duplicates.isNotEmpty()) issues += "$sideName memiliki spesies berulang: ${duplicates.joinToString()}"
            terms.forEach { term ->
                if (term.coefficient <= BigDecimal.ZERO) issues += "$sideName: koefisien ${term.speciesId} harus positif"
                val definition = definitions[term.speciesId]
                if (definition == null) {
                    issues += "$sideName: definisi ${term.speciesId} tidak ditemukan"
                } else {
                    runCatching { ChemicalFormulaParser.parse(definition.formula) }
                        .onFailure { issues += "$sideName: formula ${term.speciesId} tidak valid: ${it.message}" }
                        .onSuccess { atoms ->
                            if (allowedElements != null) {
                                val unsupported = atoms.keys - allowedElements
                                if (unsupported.isNotEmpty()) issues += "$sideName: unsur tidak terdaftar: ${unsupported.joinToString()}"
                            }
                        }
                }
            }
        }
        validateSide("Reaktan", reaction.reactants)
        validateSide("Produk", reaction.products)

        val reactantElements = elementCoefficients(reaction.reactants, definitions)
        val productElements = elementCoefficients(reaction.products, definitions)
        val residuals = (reactantElements.keys + productElements.keys).toSortedSet().associateWith { element ->
            (productElements[element] ?: BigDecimal.ZERO).subtract(reactantElements[element] ?: BigDecimal.ZERO)
        }
        val chargeReactants = chargeCoefficient(reaction.reactants, definitions)
        val chargeProducts = chargeCoefficient(reaction.products, definitions)
        val chargeResidual = chargeProducts.subtract(chargeReactants)
        if (residuals.values.any { it.compareTo(BigDecimal.ZERO) != 0 }) {
            issues += "Jumlah atom tidak seimbang: ${residuals.filterValues { it.compareTo(BigDecimal.ZERO) != 0 }}"
        }
        if (chargeResidual.compareTo(BigDecimal.ZERO) != 0) {
            issues += "Muatan tidak seimbang: residual $chargeResidual"
        }
        if (reaction.standardReactionEnthalpyKJ != null) {
            if (reaction.referenceTemperatureK == null || reaction.referenceTemperatureK <= BigDecimal.ZERO) {
                issues += "Entalpi reaksi memiliki suhu referensi yang tidak valid"
            }
            if (reaction.referencePressureBar == null || reaction.referencePressureBar <= BigDecimal.ZERO) {
                issues += "Entalpi reaksi memiliki tekanan referensi yang tidak valid"
            }
        }
        return ReactionBalanceReport(
            reactionId = reaction.id,
            valid = issues.isEmpty(),
            elementResidualBySymbol = residuals,
            chargeResidual = chargeResidual,
            issues = issues
        )
    }

    fun execute(
        state: ChemicalSystemState,
        reaction: ChemicalReaction,
        extentMol: BigDecimal,
        requireCompleteEnergyAccounting: Boolean = true
    ): ReactionExecutionResult {
        require(extentMol > BigDecimal.ZERO) { "Extent reaksi harus positif" }
        val balance = validate(reaction, state.speciesDefinitions)
        require(balance.valid) { "Reaksi ${reaction.id} tidak valid: ${balance.issues.joinToString("; ")}" }
        if (reaction.standardReactionEnthalpyKJ == null && requireCompleteEnergyAccounting) {
            error("Reaksi ${reaction.id} belum memiliki entalpi reaksi; eksekusi energi lengkap ditolak")
        }

        val reservoir = reaction.reservoir
        val beforeElements = state.elementalTotals()
        val beforeReservoirElements = state.elementalTotalsByReservoir(reservoir)
        val beforeCharge = state.chargeMolByReservoir()[reservoir] ?: BigDecimal.ZERO
        val candidateAmounts = state.speciesMolByReservoir[reservoir].orEmpty().toMutableMap()
        val deltas = linkedMapOf<String, BigDecimal>()

        reaction.reactants.forEach { term ->
            val required = term.coefficient.multiply(extentMol)
            val available = candidateAmounts[term.speciesId] ?: BigDecimal.ZERO
            require(available >= required) {
                "Reaktan ${term.speciesId} tidak cukup di $reservoir: tersedia=$available, dibutuhkan=$required"
            }
            candidateAmounts[term.speciesId] = available.subtract(required)
            deltas[term.speciesId] = (deltas[term.speciesId] ?: BigDecimal.ZERO).subtract(required)
        }
        reaction.products.forEach { term ->
            val produced = term.coefficient.multiply(extentMol)
            val current = candidateAmounts[term.speciesId] ?: BigDecimal.ZERO
            candidateAmounts[term.speciesId] = current.add(produced)
            deltas[term.speciesId] = (deltas[term.speciesId] ?: BigDecimal.ZERO).add(produced)
        }

        val updatedInventory = state.speciesMolByReservoir.toMutableMap()
        updatedInventory[reservoir] = candidateAmounts.toMap()
        val enthalpy = reaction.standardReactionEnthalpyKJ?.multiply(extentMol)
        val thermal = enthalpy?.negate()
        val updatedEnergy = if (enthalpy != null && thermal != null) {
            val chemicalByReservoir = state.energy.reactionEnthalpyChangeKJByReservoir.toMutableMap()
            val thermalByReservoir = state.energy.thermalEnergyChangeKJByReservoir.toMutableMap()
            chemicalByReservoir[reservoir] = (chemicalByReservoir[reservoir] ?: BigDecimal.ZERO).add(enthalpy)
            thermalByReservoir[reservoir] = (thermalByReservoir[reservoir] ?: BigDecimal.ZERO).add(thermal)
            ChemicalEnergyState(chemicalByReservoir.toMap(), thermalByReservoir.toMap())
        } else state.energy

        val updatedState = ChemicalSystemState(
            speciesDefinitions = state.speciesDefinitions,
            speciesMolByReservoir = updatedInventory.toMap(),
            energy = updatedEnergy
        )
        val afterElements = updatedState.elementalTotals()
        val elementalResiduals = (beforeElements.keys + afterElements.keys).toSortedSet().associateWith { element ->
            (afterElements[element] ?: BigDecimal.ZERO).subtract(beforeElements[element] ?: BigDecimal.ZERO)
        }
        check(elementalResiduals.values.all { it.compareTo(BigDecimal.ZERO) == 0 }) {
            "Transaksi ${reaction.id} melanggar konservasi unsur sistem: $elementalResiduals"
        }
        val afterReservoirElements = updatedState.elementalTotalsByReservoir(reservoir)
        val reservoirElementResiduals = (beforeReservoirElements.keys + afterReservoirElements.keys).toSortedSet().associateWith { element ->
            (afterReservoirElements[element] ?: BigDecimal.ZERO)
                .subtract(beforeReservoirElements[element] ?: BigDecimal.ZERO)
        }
        check(reservoirElementResiduals.values.all { it.compareTo(BigDecimal.ZERO) == 0 }) {
            "Transaksi ${reaction.id} melanggar konservasi unsur dalam reservoir $reservoir: $reservoirElementResiduals"
        }
        val afterCharge = updatedState.chargeMolByReservoir()[reservoir] ?: BigDecimal.ZERO
        val chargeResidual = afterCharge.subtract(beforeCharge)
        check(chargeResidual.compareTo(BigDecimal.ZERO) == 0) {
            "Transaksi ${reaction.id} melanggar konservasi muatan: $chargeResidual"
        }

        val energyAudit = if (enthalpy != null && thermal != null) {
            val net = enthalpy.add(thermal)
            check(net.compareTo(BigDecimal.ZERO) == 0) { "Neraca energi reaksi tidak tertutup: $net" }
            ReactionEnergyAudit(
                status = EnergyAccountingStatus.CLOSED,
                reactionEnthalpyChangeKJ = enthalpy,
                thermalEnergyChangeKJ = thermal,
                netEnergyResidualKJ = net,
                assumption = ADIABATIC_BOOKKEEPING_ASSUMPTION
            )
        } else {
            ReactionEnergyAudit(
                status = EnergyAccountingStatus.MISSING_REACTION_ENTHALPY,
                reactionEnthalpyChangeKJ = null,
                thermalEnergyChangeKJ = null,
                netEnergyResidualKJ = null,
                assumption = "Neraca energi belum lengkap: entalpi reaksi belum tersedia; tidak ada panas yang direka-reka."
            )
        }

        return ReactionExecutionResult(
            state = updatedState,
            reactionId = reaction.id,
            extentMol = extentMol,
            speciesDeltaMol = deltas.toMap(),
            balance = balance,
            chargeResidualMol = chargeResidual,
            elementalResidualBySymbol = elementalResiduals,
            energy = energyAudit
        )
    }

    private fun elementCoefficients(
        terms: List<ReactionTerm>,
        definitions: Map<String, ChemicalSpeciesDefinition>
    ): Map<String, BigDecimal> {
        val result = linkedMapOf<String, BigDecimal>()
        terms.forEach { term ->
            val definition = definitions[term.speciesId] ?: return@forEach
            val atoms = runCatching { ChemicalFormulaParser.parse(definition.formula) }.getOrElse { return@forEach }
            atoms.forEach { (element, count) ->
                val amount = term.coefficient.multiply(BigDecimal.valueOf(count.toLong()))
                result[element] = (result[element] ?: BigDecimal.ZERO).add(amount)
            }
        }
        return result
    }

    private fun chargeCoefficient(
        terms: List<ReactionTerm>,
        definitions: Map<String, ChemicalSpeciesDefinition>
    ): BigDecimal = terms.fold(BigDecimal.ZERO) { total, term ->
        val charge = definitions[term.speciesId]?.formalCharge ?: 0
        total.add(term.coefficient.multiply(BigDecimal.valueOf(charge.toLong())))
    }
}
