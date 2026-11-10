package org.terrachemica.app.core

import java.math.BigDecimal

/**
 * Small, transparent starter catalog. Only the methane-forming reaction has a
 * standard-state enthalpy parameter in v0.3. No catalog entry has a kinetic law,
 * so none is automatically advanced by the Earth clock.
 */
object ReactionNetworkCatalog {
    private fun bd(value: String) = BigDecimal(value)
    private fun term(id: String, coefficient: String = "1") = ReactionTerm(id, bd(coefficient))

    val reactions: List<ChemicalReaction> = listOf(
        ChemicalReaction(
            id = "ATMOSPHERIC_CO2_METHANATION",
            label = "Hidrogenasi CO₂ menjadi metana",
            reservoir = ReservoirId.ATMOSPHERIC,
            reactants = listOf(term("ATMOSPHERIC_CO2"), term("ATMOSPHERIC_H2", "4")),
            products = listOf(term("ATMOSPHERIC_CH4"), term("ATMOSPHERIC_H2O", "2")),
            standardReactionEnthalpyKJ = bd("-165.012"),
            referenceTemperatureK = bd("298.15"),
            referencePressureBar = bd("1"),
            family = ReactionFamily.REDOX,
            source = "ΔfH° gas dari NIST Chemistry WebBook: CO₂(g) -393.51; CH₄(g) -74.87; H₂O(g) -241.826; H₂(g) 0 kJ/mol. ΔrH° = Σproduk − Σreaktan = -165.012 kJ per reaksi seperti ditulis.",
            kineticsModelId = null,
            scientificLimit = "Contoh stoikiometri dan termokimia keadaan standar 298.15 K, 1 bar. Kinetika, katalis, tekanan parsial/fugasitas, spesiasi, dan ketergantungan suhu belum dimodelkan. Tidak berjalan otomatis."
        ),
        ChemicalReaction(
            id = "OCEAN_CO2_HYDRATION_DISSOCIATION",
            label = "Hidrasi CO₂ dan disosiasi pertama",
            reservoir = ReservoirId.OCEAN,
            reactants = listOf(term("OCEAN_CO2"), term("OCEAN_H2O")),
            products = listOf(term("OCEAN_HCO3"), term("OCEAN_HPLUS")),
            standardReactionEnthalpyKJ = null,
            referenceTemperatureK = null,
            referencePressureBar = null,
            family = ReactionFamily.ACID_BASE,
            source = "Persamaan kimia untuk template kesetimbangan karbonat; konstanta dan entalpi pada kondisi Archaean belum dipasok.",
            kineticsModelId = null,
            scientificLimit = "Template reaksi seimbang saja. Tidak digunakan untuk memprediksi pH atau arah kesetimbangan tanpa konstanta kesetimbangan, suhu, tekanan, salinitas, dan koefisien aktivitas."
        ),
        ChemicalReaction(
            id = "OCEAN_BICARBONATE_DISSOCIATION",
            label = "Disosiasi bikarbonat menjadi karbonat",
            reservoir = ReservoirId.OCEAN,
            reactants = listOf(term("OCEAN_HCO3")),
            products = listOf(term("OCEAN_CO3"), term("OCEAN_HPLUS")),
            standardReactionEnthalpyKJ = null,
            referenceTemperatureK = null,
            referencePressureBar = null,
            family = ReactionFamily.ACID_BASE,
            source = "Persamaan kimia untuk template kesetimbangan karbonat; konstanta dan entalpi pada kondisi Archaean belum dipasok.",
            kineticsModelId = null,
            scientificLimit = "Template reaksi seimbang saja. Tidak digunakan untuk memprediksi pH atau arah kesetimbangan tanpa konstanta kesetimbangan, suhu, tekanan, salinitas, dan koefisien aktivitas."
        ),
        ChemicalReaction(
            id = "OCEAN_CALCITE_PRECIPITATION",
            label = "Presipitasi kalsit",
            reservoir = ReservoirId.OCEAN,
            reactants = listOf(term("OCEAN_CA"), term("OCEAN_CO3")),
            products = listOf(term("OCEAN_CALCITE")),
            standardReactionEnthalpyKJ = null,
            referenceTemperatureK = null,
            referencePressureBar = null,
            family = ReactionFamily.PRECIPITATION,
            source = "Reaksi presipitasi mineral Ca²⁺ + CO₃²⁻ → CaCO₃(s); data kelarutan dan termokimia untuk kondisi model belum dipasok.",
            kineticsModelId = null,
            scientificLimit = "Template seimbang saja. Supersaturasi, aktivitas ion, nukleasi, pertumbuhan kristal, dan laju presipitasi belum dihitung."
        )
    )

    fun validateAll(state: ChemicalSystemState): ReactionCatalogReport {
        val allowed = setOf("H", "C", "N", "O", "Na", "Mg", "Al", "Si", "P", "S", "Cl", "K", "Ca", "Fe")
        val reports = reactions.map { ChemicalReactionEngine.validate(it, state.speciesDefinitions, allowed) }
        return ReactionCatalogReport(
            valid = reports.all { it.valid },
            reactionCount = reactions.size,
            balancedCount = reports.count { it.valid },
            withStandardEnthalpyCount = reactions.count { it.standardReactionEnthalpyKJ != null },
            withKineticLawCount = reactions.count { it.kineticsModelId != null },
            reports = reports
        )
    }
}

data class ReactionCatalogReport(
    val valid: Boolean,
    val reactionCount: Int,
    val balancedCount: Int,
    val withStandardEnthalpyCount: Int,
    val withKineticLawCount: Int,
    val reports: List<ReactionBalanceReport>
)
