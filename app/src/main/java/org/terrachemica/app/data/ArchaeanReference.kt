package org.terrachemica.app.data

import org.terrachemica.app.core.ReservoirId

/**
 * Selected values transcribed from the user's Archaean_of_Arus_v0.1 PDF.
 * Numeric inventories are kept as decimal strings so the source precision is not
 * altered by binary floating-point parsing in this reference layer.
 */
object ArchaeanReference {
    const val SOURCE_FILE = "Archaean_of_Arus_v0.1_Data_Mentah_Unsur_Energi_6_Reservoir.pdf"
    const val SOURCE_SOLAR_EPOCH_GA = 3.8
    const val SOURCE_ROTATION_PERIOD_HOURS = 14.0

    data class Element(val symbol: String, val name: String, val atomicNumber: Int, val atomicMass: String)
    data class EnergySource(val name: String, val value: String, val unit: String, val note: String)
    data class Inventory(val symbol: String, val formula: String, val amountMol: String)
    data class WaterStore(val label: String, val phase: String, val compartment: String, val amountMol: String)

    val elements = listOf(
        Element("H", "Hidrogen", 1, "1.008000"),
        Element("C", "Karbon", 6, "12.011000"),
        Element("N", "Nitrogen", 7, "14.007000"),
        Element("O", "Oksigen", 8, "15.999000"),
        Element("Na", "Natrium", 11, "22.990000"),
        Element("Mg", "Magnesium", 12, "24.305000"),
        Element("Al", "Aluminium", 13, "26.982000"),
        Element("Si", "Silikon", 14, "28.085000"),
        Element("P", "Fosfor", 15, "30.974000"),
        Element("S", "Sulfur", 16, "32.060000"),
        Element("Cl", "Klor", 17, "35.450000"),
        Element("K", "Kalium", 19, "39.098000"),
        Element("Ca", "Kalsium", 20, "40.078000"),
        Element("Fe", "Besi", 26, "55.845000")
    )

    val energySources = listOf(
        EnergySource("Solar Energy · partial spectral integral", "999.670976598", "W/m²", "0.5–2397.51 nm; not normalized"),
        EnergySource("Solar Energy · geometric global mean", "249.917744150", "W/m²", "Partial integral divided by four"),
        EnergySource("Geothermal Energy", "0.218", "W/m²", "Global baseline; parameter range 0.05–0.50 W/m²"),
        EnergySource("Geothermal Energy · Ocean share", "0.20056", "W/m²", "92% of geothermal baseline"),
        EnergySource("Geothermal Energy · Atmosphere share", "0.01744", "W/m²", "8% of geothermal baseline"),
        EnergySource("Planetary rotation · initial period", "14", "hours", "Initial sidereal rotation period in source"),
        EnergySource("Tidal Energy · ocean model", "22.284375474", "s", "Derived effective central lag; calibrated proxy, not measured Archean ocean lag")
    )

    val atmosphericSpecies = listOf(
        Inventory("N₂", "N2", "5.56248712e19"),
        Inventory("CO₂", "CO2", "1.18023136e19"),
        Inventory("H₂", "H2", "1.03064453e19"),
        Inventory("CH₄", "CH4", "3.23774e16"),
        Inventory("CO", "CO", "7.4175e15"),
        Inventory("H₂S", "H2S", "1.524e14"),
        Inventory("SO₂", "SO2", "8.1e12")
    )

    val oceanSpecies = listOf(
        Inventory("Cl⁻", "Cl", "6.05202201530543e20"),
        Inventory("Na⁺", "Na", "4.899999811e20"),
        Inventory("Ca²⁺", "Ca", "2.799999892e19"),
        Inventory("Mg²⁺", "Mg", "2.799999892e19"),
        Inventory("K⁺", "K", "1.1199999568e19"),
        Inventory("HCO₃⁻", "HCO3", "8.21565055749e18"),
        Inventory("CO₂(aq)", "CO2", "4.6199998218e18"),
        Inventory("H₄SiO₄", "silicicAcid", "9.3799996382e17"),
        Inventory("Fe²⁺", "Fe", "1.1199999568e17"),
        Inventory("CO₃²⁻", "CO3", "1.529825722e15"),
        Inventory("SO₄²⁻", "sulfate", "1.399999946e15"),
        Inventory("PO₄³⁻", "phosphate", "8.8199997e13")
    )

    val crustMinerals = listOf(
        Inventory("Forsterite", "Mg2SiO4", "1.927800481502777e21"),
        Inventory("Fayalite", "Fe2SiO4", "2.74461768497223e20"),
        Inventory("Anorthite", "CaAl2Si2O8", "9.56438995e20"),
        Inventory("Albite", "NaAlSi3O8", "9.56438995e20"),
        Inventory("Hedenbergite", "CaFeSi2O6", "7.1375605e20"),
        Inventory("Diopside", "CaMgSi2O6", "7.1375605e20"),
        Inventory("Ferrosilite", "FeSiO3", "9.563753e20"),
        Inventory("Enstatite", "MgSiO3", "9.563753e20"),
        Inventory("Apatite", "apatite", "2.77504e11"),
        Inventory("Serpentine", "serpentine", "0"),
        Inventory("Brucite", "brucite", "0"),
        Inventory("Magnetite", "magnetite", "0")
    )

    val hydrothermalSpecies = listOf(
        Inventory("Cl⁻", "Cl", "2.3343514e13"),
        Inventory("Na⁺", "Na", "1.89e13"),
        Inventory("Ca²⁺", "Ca", "1.08e12"),
        Inventory("Mg²⁺", "Mg", "1.08e12"),
        Inventory("K⁺", "K", "4.32e11"),
        Inventory("HCO₃⁻", "HCO3", "3.16889e11"),
        Inventory("CO₂(aq)", "CO2", "1.782e11"),
        Inventory("H₄SiO₄", "silicicAcid", "3.618e10"),
        Inventory("Fe²⁺", "Fe", "4.32e9"),
        Inventory("CO₃²⁻", "CO3", "5.9e7"),
        Inventory("SO₄²⁻", "sulfate", "5.4e7"),
        Inventory("PO₄³⁻", "phosphate", "3e6"),
        Inventory("H₂", "H2", "0"),
        Inventory("SiO₂(aq)", "SiO2", "0")
    )

    val waterStores = listOf(
        WaterStore("Lower atmosphere water vapor", "vapor", "lower", "8.95337107815e17"),
        WaterStore("Middle atmosphere water vapor", "vapor", "middle", "1.8272185874e16"),
        WaterStore("Surface ocean", "liquid", "surface", "5.096318510939706e21"),
        WaterStore("Intermediate ocean", "liquid", "intermediate", "2.038527404375882e22"),
        WaterStore("Deep ocean", "liquid", "deep", "7.64447776640956e22"),
        WaterStore("Hydrothermal recharge", "liquid_pore_brine", "recharge", "1.498727747e15"),
        WaterStore("Hydrothermal reaction", "liquid_pore_brine", "reaction", "5.99491099e14"),
        WaterStore("Hydrothermal discharge", "liquid_pore_brine", "discharge", "8.99236648e14")
    )

    /**
     * Elemental atom inventories transcribed from the six reservoir ledgers in
     * the supplied PDF (pages 6, 11, 14, 16, 18 and 22). Values are mol of atoms,
     * not mol of molecules. Every zero is an explicit source value.
     * This is a static 3.8 Ga reference snapshot, not a Hadean starting state.
     */
    val elementalInventoryByReservoir: Map<ReservoirId, Map<String, String>> = mapOf(
        ReservoirId.ATMOSPHERIC to mapOf(
            "H" to "2.2569923587378e19", "C" to "1.18421085e19", "N" to "1.112497424e20",
            "O" to "2.4525670193689e19", "Na" to "0", "Mg" to "0", "Al" to "0",
            "Si" to "0", "P" to "0", "S" to "1.605e14", "Cl" to "0", "K" to "0",
            "Ca" to "0", "Fe" to "0"
        ),
        ReservoirId.OCEAN to mapOf(
            "H" to "2.03864708088001e23", "C" to "1.2837180205013e19", "N" to "0",
            "O" to "1.019640197122424e23", "Na" to "4.899999811e20", "Mg" to "2.799999892e19",
            "Al" to "0", "Si" to "9.3799996382e17", "P" to "8.8199997e13",
            "S" to "1.399999946e15", "Cl" to "6.05202201530543e20",
            "K" to "1.1199999568e19", "Ca" to "2.799999892e19", "Fe" to "1.1199999568e17"
        ),
        ReservoirId.TERRESTRIAL to mapOf(
            "H" to "0", "C" to "0", "N" to "0", "O" to "0", "Na" to "0", "Mg" to "0",
            "Al" to "0", "Si" to "0", "P" to "0", "S" to "0", "Cl" to "0", "K" to "0",
            "Ca" to "0", "Fe" to "0"
        ),
        ReservoirId.CRUST to mapOf(
            "H" to "2.77504e11", "C" to "0", "N" to "0", "O" to "3.841539732360756e22",
            "Na" to "9.56438995e20", "Mg" to "5.525732313005554e21", "Al" to "2.869316985e21",
            "Si" to "1.1752232025e22", "P" to "8.32512e11", "S" to "0", "Cl" to "0",
            "K" to "0", "Ca" to "2.38395109638752e21", "Fe" to "2.219054886994445e21"
        ),
        ReservoirId.HYDROTHERMAL_FLUID to mapOf(
            "H" to "5.995372596e15", "C" to "4.95148e11", "N" to "0",
            "O" to "2.998907688e15", "Na" to "1.89e13", "Mg" to "1.08e12",
            "Al" to "0", "Si" to "3.618e10", "P" to "3e6", "S" to "5.4e7",
            "Cl" to "2.3343514e13", "K" to "4.32e11", "Ca" to "1.08e12",
            "Fe" to "4.32e9"
        ),
        ReservoirId.MAGMA to mapOf(
            "H" to "0", "C" to "0", "N" to "0", "O" to "0", "Na" to "0", "Mg" to "0",
            "Al" to "0", "Si" to "0", "P" to "0", "S" to "0", "Cl" to "0", "K" to "0",
            "Ca" to "0", "Fe" to "0"
        )
    )

    data class StoichiometricSpecies(val label: String, val formula: String, val amountMol: String)

    private fun stoichiometricList(values: List<Inventory>): List<StoichiometricSpecies> = values.map { item ->
        val formula = when (item.formula) {
            "silicicAcid" -> "H4SiO4"
            "sulfate" -> "SO4"
            "phosphate" -> "PO4"
            // The source mineral label explicitly includes Ca5(PO4)3OH.
            "apatite" -> "Ca5(PO4)3OH"
            "serpentine" -> "Mg3Si2O5(OH)4"
            "brucite" -> "Mg(OH)2"
            "magnetite" -> "Fe3O4"
            else -> item.formula
        }
        StoichiometricSpecies(item.symbol, formula, item.amountMol)
    }

    /** Formula-based inventories for source reconciliation; water remains a sub-store. */
    val stoichiometricSpeciesByReservoir: Map<ReservoirId, List<StoichiometricSpecies>> = mapOf(
        ReservoirId.ATMOSPHERIC to stoichiometricList(atmosphericSpecies),
        ReservoirId.OCEAN to stoichiometricList(oceanSpecies),
        ReservoirId.TERRESTRIAL to emptyList(),
        ReservoirId.CRUST to stoichiometricList(crustMinerals),
        ReservoirId.HYDROTHERMAL_FLUID to stoichiometricList(hydrothermalSpecies),
        ReservoirId.MAGMA to emptyList()
    )

    /** Molecules of H2O by owning reservoir; these are not additional to H/O ledgers. */
    val waterMoleculesByReservoir: Map<ReservoirId, List<String>> = mapOf(
        ReservoirId.ATMOSPHERIC to listOf("8.95337107815e17", "1.8272185874e16"),
        ReservoirId.OCEAN to listOf("5.096318510939706e21", "2.038527404375882e22", "7.64447776640956e22"),
        ReservoirId.TERRESTRIAL to emptyList(),
        ReservoirId.CRUST to emptyList(),
        ReservoirId.HYDROTHERMAL_FLUID to listOf("1.498727747e15", "5.99491099e14", "8.99236648e14"),
        ReservoirId.MAGMA to emptyList()
    )

    const val ATMOSPHERE_TOTAL_DRY_GAS_MOL = "7.77735855e19"
    const val ATMOSPHERE_TOTAL_WATER_MOL = "9.13609293688928e17"
    const val NOTE = "Static inventory snapshot only. This reference does not include process equations, solver, allocations, or audited runtime diagnostics."
}
