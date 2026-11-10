# Scientific assumptions and provenance

## Time coordinates

- `ageSinceFormationMa`: elapsed millions of years since the adopted Earth-formation epoch.
- `lookbackMa = 4540 - ageSinceFormationMa`: million years before present.
- Earth-age reference: 4.540 Ga.
- Solar-age reference: 4.568 Ga.
- Solar age during the adopted Earth-formation epoch: approximately 0.028 Ga.

The geological eon boundaries in the prototype are taken from the Geological Survey of Western Australia's geological-time page (updated 8 July 2025): Hadean 4.567–4.031 Ga; Archean 4.031–2.500 Ga; Proterozoic 2.500–0.5388 Ga; Phanerozoic 0.5388 Ga to present. This slightly predates the model's selected Earth-formation age because the Hadean time scale begins with the early Solar System chronology; the model itself starts at 4.540 Ga.

## Solar luminosity

The code uses the Gough (1981) approximation as summarized in Feulner (2012):

`L(t) / L(now) = 1 / (1 + 0.4 * (1 - t / t_now))`

where `t` is the Sun's age and `t_now` is the adopted present solar age. The approximation is used only when `t >= 0.2 Ga`; earlier values are shown as uncalibrated. It is a bolometric luminosity approximation, not a wavelength-resolved spectrum and not a complete young-star/pre-main-sequence model.

The faint-young-Sun review notes that early Earth solar input was roughly 25% lower than today in standard solar-evolution estimates, while the climate resolution of the faint-young-Sun problem remains unsettled. The prototype therefore does not infer ancient surface temperature from solar luminosity alone.

## Spectral anchors

Zenodo record 17981836 (`stellar_spectra/solar`) lists `Sun4.4Ga.txt`, `Sun3.8Ga.txt`, `Sun2.7Ga.txt`, `Sun2.4Ga.txt`, `Sun1.8Ga.txt`, `Sun0.6Ga.txt`, and `SunModern.txt`. The record attributes the past spectra to VPL / Claire et al. (2012), whose paper covers solar flux evolution from 0.1 nm to 160 μm. The user-provided PDF reports a particular 3.8 Ga extraction window of 0.5–2397.51 nm with 26,150 spectral points and a partial integral of 999.670976598 W/m², not normalized. The PDF's printed integral must not be mistaken for a normalized total solar constant.

## Archaean of Arus v0.1

The included JSON preserves selected static values from the user-provided 33-page PDF. The source explicitly excludes engine code, equations, solver procedures, allocation methods, and audit/diagnostic tables. The six-reservoir inventories are therefore a reference snapshot, not an executable Earth-system model. In particular, zero values explicitly printed for Terrestrial and Magma stores are not interpreted as proof that the real Earth had no such stores.

## Sources

- User-provided file: `Archaean_of_Arus_v0.1_Data_Mentah_Unsur_Energi_6_Reservoir.pdf`, pages 2–5 (elements and energy), 6–22 (reservoir data), 23–32 (atmospheric spatial subinventory), 33 (scope/exclusions).
- USGS, *Age of the Earth*: https://pubs.usgs.gov/gip/geotime/age.html
- NASA Space Place, *How Old Is the Sun?*: https://spaceplace.nasa.gov/sun-age/en/
- Moynier et al. age reference as summarized in: https://academic.oup.com/mnras/article/534/3/2968/7811326
- Geological Survey of Western Australia, *Geological time*: https://www.wa.gov.au/organisation/department-of-mines-petroleum-and-exploration/geological-survey-of-western-australia/geological-time
- Feulner (2012), *The faint young Sun problem*, Reviews of Geophysics: https://doi.org/10.1029/2011RG000375
- Claire et al. (2012), *The evolution of solar flux from 0.1 nm to 160 μm*: https://doi.org/10.1088/0004-637X/757/1/95
- Zenodo record 17981836, `stellar_spectra/solar`: https://zenodo.org/records/17981836


## v0.2 elemental and stoichiometric ledger validation

- The six reservoir elemental ledgers contain 14 element symbols for each of six reservoirs (84 source values), copied as decimal strings from the supplied PDF. Source page references: atmospheric p.6; ocean p.11; terrestrial p.14; crust p.16; hydrothermal fluid p.18; magma p.22.
- Source elemental ledger values are mol of atoms, not mol of molecules.
- The stoichiometry audit reconstructs atom-mole contributions from 45 species entries and eight positive H₂O sub-stores. H₂O contributes two H atoms and one O atom per molecule. Water stores are nested within their owning reservoirs and are not counted as extra reservoirs.
- Formula aliases are resolved solely for composition accounting: `silicicAcid` → `H4SiO4`; `sulfate` → `SO4`; `phosphate` → `PO4`; apatite → `Ca5(PO4)3OH` (shown in the source mineral label); serpentine → `Mg3Si2O5(OH)4`; brucite → `Mg(OH)2`; magnetite → `Fe3O4`. Ionic charge is not included in this atom-count parser.
- Relative reconciliation tolerance is `1e-9`. Current maximum relative residual is approximately `6.6690949108e-10`; no source values are corrected, normalized, or overwritten.
- The atom-transfer engine preserves totals for a selected element, supports immutable multi-transfer batches, rejects negative/overspent stores, and audits the result. It is a bookkeeping kernel, not yet a chemical reaction or geophysical-process solver.
- Archaean inventory remains a static 3.8 Ga reference checkpoint; it is not applied to other points in the Earth timeline.


## v0.3 reaction and energy bookkeeping

The reaction kernel stores chemical formula and formal charge as separate fields, then validates (1) each tracked element's stoichiometric coefficient sum and (2) formal-charge coefficient sum. It executes an explicitly supplied positive extent only. It does not calculate that extent from elapsed time, concentrations, temperature, pressure, or catalyst abundance. This avoids inventing a kinetic law.

The starter reaction catalog contains four balanced equations:

1. Atmospheric methanation: `CO2(g) + 4 H2(g) -> CH4(g) + 2 H2O(g)`
2. Ocean CO2 hydration/first dissociation: `CO2(aq) + H2O(l) -> HCO3- + H+`
3. Bicarbonate dissociation: `HCO3- -> CO3^2- + H+`
4. Calcite precipitation: `Ca^2+ + CO3^2- -> CaCO3(s)`

Only reaction 1 is assigned a standard-state reaction enthalpy. At 298.15 K and 1 bar, its value is derived from NIST Chemistry WebBook standard gas-phase formation enthalpies: CO2(g) -393.51 kJ/mol, CH4(g) -74.87 kJ/mol, H2O(g) -241.826 kJ/mol, and H2(g) 0 kJ/mol by the elemental standard-state convention. For the equation as written, `ΔrH° = [-74.87 + 2(-241.826)] - [-393.51 + 4(0)] = -165.012 kJ`.

This enthalpy is a standard-state demonstration only. It is not temperature-corrected to an Archaean atmosphere, does not include fugacity/non-ideality, and does not establish that methanation proceeded at a relevant rate in the modeled atmosphere. No reaction has a kinetic law, catalyst model, or equilibrium solver, so the Earth clock does not automatically execute any catalog reaction.

For a reaction with known ΔrH°, the current energy ledger books `ΔH_chemical` and an equal-and-opposite `ΔE_thermal = -ΔH_chemical` under a closed, adiabatic bookkeeping assumption for the host reservoir. This closes the transaction-level energy residual but does not predict temperature: the thermal value is an incremental energy account, not absolute internal energy. Heat capacity, radiative loss, work, conduction, heat transfer between reservoirs, and temperature-dependent enthalpies remain unimplemented. Reactions without enthalpy values are rejected by default when complete energy accounting is required.

The Archaean chemistry factory creates a working molecular/ionic state from the source snapshot. Eight source H2O stores are aggregated into three parent-reservoir water pools in the v0.3 reaction state; the original eight entries remain preserved in the reference JSON/PDF. `H+` and `CaCO3(s)` are zero-initialized optional species added only to support balanced reaction templates; they are not claimed as measured source inventories.

### Thermochemical references

- NIST Chemistry WebBook, carbon dioxide gas-phase standard formation enthalpy: -393.51 ± 0.13 kJ/mol. https://webbook.nist.gov/cgi/cbook.cgi?Name=carbon+dioxide&cTG=on
- NIST Chemistry WebBook, methane gas-phase standard formation enthalpy: -74.87 kJ/mol (Chase, 1998 review). https://webbook.nist.gov/cgi/cbook.cgi?ID=C74828&Mask=8EF
- NIST Chemistry WebBook, water vapor standard formation enthalpy: -241.826 ± 0.040 kJ/mol. https://webbook.nist.gov/cgi/cbook.cgi?Name=water&cTG=on

Enthalpy uncertainty is not yet propagated, and the engine does not contain a temperature-dependent thermodynamic database.


## v0.5 atmospheric radiative-transfer boundary

The v0.5 shortwave module is an absorption-only Beer–Lambert column. Its current calibration uses a single effective broadband optical depth in each of the eight source wavelength bands. The downward and reflected upward passes are separately accounted for; the resulting energy residual closes to floating-point roundoff. The effective lower-boundary reflectance is a lumped calibration parameter and must not be interpreted as a measured bare-ground albedo. Unresolved cloud, aerosol, and atmospheric reflection are not physically separated.

The modern calibration follows NASA's rounded global energy partition (approximately 29% reflected, 23% atmospheric shortwave absorption, 48% surface shortwave absorption) and a nominal 1361 W m-2 modern TSI. A one-layer grey longwave slab is calibrated to a 288.15 K modern surface-temperature target. Its grey emissivity and resulting effective slab temperature are diagnostic parameters, not line-by-line or observed atmospheric properties.

When applied at ancient solar ages, the model holds those modern effective optical fractions and grey emissivity fixed while the bolometric input is scaled by the existing Gough luminosity approximation. Such results are explicitly labelled as a solar-only sensitivity experiment and are **not** palaeoclimate reconstructions. This distinction is critical because atmospheric composition, pressure, cloudiness, aerosol loading, surface albedo, ocean heat capacity, circulation, evaporation, and geological boundary conditions all change through Earth history.

A composition-dependent extension needs spectral absorption cross-sections or line parameters across wavelength, temperature and pressure, plus vertical profiles. HITRAN supplies molecular spectroscopic parameters for radiative-transfer calculations; it is not by itself a complete atmosphere or climate solver. v0.5 does not derive its optical depth from the Archaean reference gas inventories.

### v0.5 radiative references

- NASA Earth Observatory, modern Earth energy budget (global mean ~340 W m-2; approximately 29% reflected, 23% absorbed in atmosphere, 48% absorbed at surface; average surface temperature ~15 C): https://science.nasa.gov/earth/earth-observatory/climate-and-earths-energy-budget/
- NASA GSFC Solar Irradiance Science (TSI reference near 1361 W m-2; globally averaged input near 340 W m-2): https://earth.gsfc.nasa.gov/climate/projects/solar-irradiance/science
- NASA NSSDC Earth Fact Sheet (TSI 1361 W m-2; Bond albedo 0.294; black-body temperature 254 K): https://nssdc.gsfc.nasa.gov/planetary/factsheet/earthfact.html
- HITRAN database overview: https://www.hitran.org/about/
- HITRAN line-by-line parameters and units: https://www.hitran.org/docs/definitions-and-units/
