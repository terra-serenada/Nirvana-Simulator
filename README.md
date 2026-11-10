# Terra Chemica — Earth Evolution (Android prototype v0.8)

**Target:** Samsung Galaxy A15 4G. The phone is the local compute device and the display. A server is not required for the prototype's clock, timeline, reference-data handling, radiative diagnostics, weather-column calculations, or cross-section renderer.

## What is implemented

## v0.8 visual and time changes

- The main scene is now a 2D conceptual cutaway: early atmosphere, clouds, ocean, basaltic crust, mantle, and core are visible together instead of a decorative globe.
- First launch and upgrade to v0.8 open at the 3.8 Ga Archaean reference anchor (740 Ma since Earth formation) and remain paused so the user can inspect the scene.
- A dedicated “KEMBALI KE ARKEAN · 3,8 Ga” control returns to the source-data anchor at any time.
- Scientific Daily remains exact (1 real second = 1 simulated day); the independently accelerated weather clock continues at 1 real second = 1 weather hour while geological time is paused.
- Auto Journey is slowed from 28 to 150 seconds per milestone segment and holds for 12 seconds at each milestone.
- Low cloud fractions remain visible as thin cloud wisps. Rain is drawn only for positive model precipitation; lightning flashes only after the model's event counter advances.
- The cutaway geology and mantle convection arrows are explanatory artwork. They are not a solved geodynamic model or a reconstruction of Archaean paleogeography.

- v0.7: local model-driven weather column coupled to the Gough solar-luminosity fraction and the existing one-layer grey radiative-equilibrium surface temperature. The model advances surface evaporation, atmospheric water vapor, humidity, condensation, cloud liquid water, rainfall, and return precipitation to the surface reservoir.
- v0.7: convection proxy and cloud-coverage-dependent electrical-charge accumulator generate discrete lightning events. The cross-section's cloud coverage, rain rate, cloud darkness, and lightning flashes are rendered from the computed weather state rather than from an independent repeating animation.
- v0.7: on-screen weather diagnostics include relative humidity, cloud fraction, vapor column, cloud liquid water, rainfall rate, convection index, lightning-event count, and water-budget residual. The CUACA ON/OFF control still toggles the weather overlay.
- v0.6: first visual-only cloud/rain/lightning layer; superseded in v0.7 by the coupled column model.

- v0.5: band-resolved absorption-only shortwave atmospheric column with downward and reflected upward passes, explicit per-band energy accounting, and a modern effective calibration.
- v0.5: one-layer grey longwave radiative-equilibrium sensitivity linked to the Gough bolometric solar-luminosity fraction; the modern checkpoint is calibrated to 288.15 K.
- v0.5: UI diagnostics for partial-domain atmospheric absorption, surface absorption, reflected flux, bolometric TSI scaling, effective grey temperatures, and energy residuals.
- v0.5: all pre-modern grey temperatures are explicitly labelled as a solar-only sensitivity with modern effective radiative properties held fixed, not as palaeoclimate reconstructions.
- v0.4: parser for VPL/Claire solar spectra; converts spectral flux density from erg s^-1 cm^-2 nm^-1 to W m^-2 nm^-1.
- v0.4: trapezoidal wavelength integration with interpolated boundaries across the eight source-defined wavelength bands (0.5–2397.51 nm).
- v0.4: exact-epoch spectrum selection and log-flux interpolation between neighboring paleospectral anchors; refuses time extrapolation and resamples to the common wavelength domain when native grids differ.
- v0.4: top-of-atmosphere partial-band irradiance at 1 AU, eight wavelength-band contributions, and global geometric mean F/4. Reflected/absorbed flux is only calculated when an albedo is explicitly provided; no default albedo is invented.
- v0.4: runtime 3.8 Ga spectrum benchmark against the eight band integrals and partial total printed in the supplied Archaean of Arus PDF. Runtime comparison is performed after the phone downloads and validates Sun3.8Ga.txt.
- v0.4: fixed-duration Auto Journey segments with milestone holds; avoids the asymptotic slowdown caused by recalculating the remaining distance as a constant travel denominator. Scientific Daily still remains exactly 1 real second per simulated day.
- v0.4: separate download/calculation executors, checksum verification cache, and a three-spectrum in-memory LRU cache to reduce work on the A15.
- v0.3: balanced reaction-network validator with exact decimal stoichiometric coefficients and formal-charge accounting.
- v0.3: transactional reaction execution that checks reagent availability, immutable state updates, atom conservation, charge conservation, and reaction-energy bookkeeping.
- v0.3: Archaean molecular/ionic working-state factory from the six-reservoir reference; water stores are aggregated per parent reservoir in the reaction kernel while raw sub-store values remain in the reference dataset.
- v0.3: starter catalog of four balanced reactions: atmospheric CO₂ methanation, aqueous CO₂ hydration/dissociation, bicarbonate dissociation, and calcite precipitation.
- v0.3: one reaction carries standard-state reaction enthalpy calculated from NIST formation-enthalpy data; three templates remain blocked from complete-energy execution because their enthalpies are not parameterized. No reaction has a kinetic law, so the catalogue is not auto-fired.
- v0.2: full 14-element × six-reservoir atom-mole reference ledger transcribed from the supplied PDF.
- v0.2: exact-decimal elemental transfers with per-transfer conservation audits and atomic batch rejection.
- v0.2: nested chemical-formula parser plus stoichiometry audit reconciling 45 species and 8 positive H₂O stores with 84 element-ledger entries.
- v0.2: source-rounding tolerance is explicit (relative `1e-9`); source inventory values are not silently changed.
- v0.2: app displays that the Archaean inventory is a 3.8 Ga checkpoint only and is not extrapolated to the Hadean or modern Earth.
- v0.2: simulation age is persisted as a decimal string instead of a 32-bit float, preserving sub-million-year time resolution.

- Native Android starter project (Kotlin, no third-party runtime library).
- Earth-evolution clock beginning at an Earth age of 4.540 Ga and ending at the modern reference point.
- Exact **Scientific Daily** mode: 1 real second advances exactly 1 simulated day.
- **Auto Journey** mode: long geological intervals are presentation-compressed between milestones, with short holds at reference points. This does not claim that every skipped day has been independently numerically solved.
- Eon labels based on the geological boundaries used by the Geological Survey of Western Australia: Hadean 4.567–4.031 Ga, Archean 4.031–2.500 Ga, Proterozoic 2.500–0.5388 Ga, Phanerozoic 0.5388 Ga–present.
- Solar age derived from the adopted 4.568 Ga solar reference and the 4.540 Ga Earth-age reference.
- Gough (1981) bolometric luminosity approximation used only for solar ages >= 0.2 Ga. Earlier pre-main-sequence/very-young-Sun conditions are explicitly marked uncalibrated.
- Automatic caching of the published VPL/Claire et al. solar spectral anchor files from Zenodo, with MD5 verification. First run downloads seven spectrum files if the phone has internet access (roughly several MB; exact transfer depends on server response).
- Archaean of Arus v0.1 reference data included as an asset and selected values retained as strings to avoid changing their printed precision.
- Exact-decimal conservative atom-mole transfer kernel for moving one element between the six reservoirs, with per-transfer and batch conservation audits (no reaction or transport rates are invented).
- Screen-on operation while the app is open, local progress persistence, pause/resume, and reopening at the saved geological age after the first v0.8 launch. The v0.1 clock runs only while the activity is in the foreground; it does not claim continuous background computation after Android suspends the app.
- Conceptual cross-section view with atmosphere, ocean, crust, mantle, and core layers; it is explanatory artwork, not paleogeography or geodynamics.

## Important scope boundary

This is a **first executable software foundation**, not yet a validated, fully coupled Earth-system simulator. In particular:

1. The v0.8 cross-section's coast and internal layers are illustrative, not a reconstruction of paleogeography or geodynamics.
2. The six-reservoir values in `archaen_reference.json` are a static Archaean reference snapshot. They are not a Hadean initial state and must not be interpreted as modern inventories.
3. The uploaded Archaean PDF deliberately excludes process equations, solver procedures, allocation methods, and runtime diagnostics. The app therefore does not fabricate those missing processes.
4. v0.4–v0.5 use the included partial solar spectral domain for wavelength-band diagnostics; it is not labelled bolometric TSI. v0.5 adds an absorption-only Beer–Lambert shortwave column and a one-layer grey longwave equilibrium calibrated to rounded modern global energy-budget fractions. These are reduced effective models, not molecular spectral radiative transfer or a validated climate model. Scattering, aerosols, multiple reflections, gas-specific cross-sections, full latent/sensible heat pathways, and ocean heat storage remain unimplemented.
5. The Gough approximation is not used for the first 0.2 Gyr of solar age. The initial Earth-formation stage consequently has an explicit solar-luminosity calibration gap.
6. The source PDF gives a 14-hour initial sidereal rotation reference in the Archaean dataset, but does not provide a complete rotation history. The prototype only displays the 14-hour value at the exact 3.8 Ga anchor and the modern sidereal-day reference at present; it does not invent a continuous rotation curve.
7. Climate, ocean circulation, plate tectonics, biosphere, and fully coupled mass/energy exchange still need scientific implementation and validation. The v0.3 chemistry module is a stoichiometric transaction framework with a small demonstration catalog, not a calibrated atmospheric/ocean chemistry solver.
8. v0.7 weather is a **single representative atmospheric column**, not a spatial weather map or global circulation model. It uses fixed effective air-column mass and a fixed modern-ocean-equivalent surface-water reservoir across eras. Its convection and lightning-charge indices are nondimensional proxies, not CAPE or electrical field calculations. Its temperature forcing inherits the v0.5 fixed-modern-optics grey sensitivity; therefore rain and lightning are illustrative process-model outputs, not reconstructed palaeoclimate or forecasts. See `docs/ATMOSPHERIC_WEATHER_V0.7.md` for equations and limits.
10. The v0.8 cross-section is a schematic explanatory cutaway. Its atmospheric weather is still one representative column projected onto the scene, and its mantle/core layers are not numerically evolved.
9. The weather clock is accelerated separately from geological time: 1 real second advances 1 weather hour. The Gough solar-luminosity approximation is unavailable for the first 0.2 Ga of solar age; weather is explicitly withheld during that interval rather than filling the gap with invented forcing.

The Auto Journey mode is a viewing aid to make a multi-billion-year journey watchable. The user's base scientific clock remains 1 second = 1 simulated day in Scientific Daily mode. Long-time compression must never be confused with model integration accuracy.

## Solar reference handling

The uploaded PDF points to the VPL/Claire et al. (2012) 3.8 Ga spectrum. The Zenodo record used here also lists past spectral files for 4.4, 3.8, 2.7, 2.4, 1.8, and 0.6 Ga plus a modern spectrum. They are cached inside the app's private storage under `files/solar_spectra/` and checked against the MD5 values published on the Zenodo record.

The reference solar age used in the code is 4.568 Ga. The Earth-age reference is 4.540 Ga. At Earth formation, the implied solar age is therefore about 0.028 Ga; the Gough main-sequence approximation is deliberately disabled there because the reviewed approximation is stated to agree with standard solar models except for approximately the first 0.2 Gyr of the young Sun's life.

## Build and install on Samsung Galaxy A15 4G

1. Extract this ZIP on a computer with Android Studio, JDK 17, and Gradle 8.9 or newer installed. The project uses Android Gradle Plugin 8.7.3; a Gradle wrapper is not bundled in this source archive.
2. Open the `terra_chemica_android` folder as an existing Android project.
3. Allow Gradle sync and install Android SDK Platform 35 / Build Tools 35 when prompted.
4. Use **Build → Build Bundle(s) / APK(s) → Build APK(s)**. The debug APK is normally created at `app/build/outputs/apk/debug/app-debug.apk`.
5. Enable Developer Options and USB debugging on the Galaxy A15 4G, connect it by USB, and press Run (or install the debug APK).
6. Keep the app open for the first viewing session. The prototype keeps the display on while active; Android may suspend ordinary app execution after the app is backgrounded or the screen is turned off.

Package: `org.terrachemica.app`  
Version: `0.7.0`  
Minimum Android: API 26  
Compile/target SDK: 35

## Core logic check (without Android SDK)

Run from this folder:

```bash
bash tools/check_core.sh
```

This validates the pure Kotlin clock, epoch/solar-reference calculations, exact-decimal species/element transfers, formula parsing, Archaean species-to-element reconciliation, reaction atom/charge balance, transactional reaction extents, energy bookkeeping, spectral unit conversion, spectral integration, band-edge interpolation, paleotime spectral interpolation, TOA geometry, shortwave Beer–Lambert energy closure, grey longwave radiative-equilibrium closure, model-driven rainfall/lightning, water-cycle mass closure, and the PDF benchmark constants. It also checks the JSON assets. It does not build the Android UI. The pure Kotlin core checks were run successfully with Kotlin 1.9. The Android SDK and Gradle Android build toolchain are not installed in this environment, so an installable APK has not been produced here.

## Next engineering stages

- v0.3 delivered: define sourced reaction networks, stoichiometric reaction execution, charge balance, and energy bookkeeping.
- v0.4 delivered: source-unit conversion, spectral integration, interpolation between discrete paleosolar anchors, and TOA radiation diagnostics.
- v0.5 delivered: effective absorption-only shortwave transfer and one-layer grey longwave equilibrium, with explicit modern calibration and deep-time sensitivity caveat.
- v0.6–v0.7 delivered: conceptual storm rendering followed by a model-driven single-column moisture/precipitation/lightning demonstrator with a closed water budget.
- v0.9: planned composition-dependent absorption using spectroscopic cross-sections, a multi-layer longwave solver, explicit convective/latent energy pathways, and a Hadean-to-Archaean initial-state bridge with uncertainty bands.
- v0.9: low-resolution global spatial grid, then adaptive refinement; keep memory and thermal limits measurable on A15.
- v1.0: ocean/atmosphere/land/ice interactions and paleogeography data layers.
- v1.1: biosphere and geochemical feedbacks.
- v1.2: validated Earth-Evolution scenario with reproducible checkpoints and automated recovery.

No stage should silently turn missing observations into precise-looking facts.

## v0.2 scientific validation boundary

The source reconciliation is a bookkeeping validation, not a physical simulation. The parser derives elemental atom-moles from source species formulae and source H₂O stores. The source labels `silicicAcid`, `sulfate`, and `phosphate` are mapped to the displayed chemical formulae H₄SiO₄, SO₄, and PO₄; the source apatite mineral label explicitly includes Ca₅(PO₄)₃OH. Serpentine, brucite, and magnetite use their named mineral formulae. These mappings are used only for composition checks.

The maximum relative residual in the current source reconciliation is approximately `6.6691e-10`, within the explicitly configured `1e-9` relative tolerance. This small difference is retained as a source-precision residual. The engine does not normalize or overwrite the PDF values.

The v0.2 transfer kernel moves atom-moles of a selected element between reservoirs. The v0.3 reaction kernel adds limited species conversion through balanced reaction extents, but it still does not provide calibrated reaction kinetics, gas-liquid partitioning, ocean circulation, weathering, volcanism, heat capacity, or inter-reservoir energy transport. Those require process parameters with provenance.


## v0.3 reaction-engine scope and limits

The v0.3 reaction engine validates elemental stoichiometry and formal-charge balance independently. Reaction extents are explicit inputs; the engine does not infer them from elapsed time or inventory. Every transaction checks reagent availability and confirms exact conservation of all formula-derived atom-moles and charge in the host reservoir. Invalid transactions leave the immutable input state unchanged.

The sample atmospheric reaction is:

`CO2(g) + 4 H2(g) -> CH4(g) + 2 H2O(g)`

Its standard reaction enthalpy is calculated at 298.15 K and 1 bar using NIST Chemistry WebBook gas-phase standard formation enthalpies: CO2(g) = -393.51 kJ/mol, CH4(g) = -74.87 kJ/mol, H2O(g) = -241.826 kJ/mol, and H2(g) = 0 by elemental standard-state convention. Thus ΔrH° = -165.012 kJ per reaction as written. This is a standard-state reference, not a calibrated Archaean enthalpy at the evolving atmospheric temperature and pressure.

For this demonstration only, the reaction enthalpy change is paired with an equal-and-opposite thermal-pool change under an explicitly adiabatic bookkeeping assumption. The thermal pool is a relative energy increment, not absolute planetary internal energy and not a temperature prediction. Heat capacity, temperature response, radiation, work, conduction, and energy transfer between reservoirs are not yet modeled.

The three ocean reaction entries are balanced equation templates. Because their condition-specific reaction enthalpies, equilibrium constants/activity corrections, and kinetic laws are not supplied, the engine refuses complete-energy execution for them. The atmospheric methanation entry also has no rate law, catalyst model, or pressure/fugacity correction and is never automatically advanced. Its execution in tests is a bookkeeping demonstration only; it is not evidence that the reaction proceeds at a geologically relevant rate in the Archaean atmosphere.

### v0.3 thermochemical references

- NIST Chemistry WebBook, Carbon dioxide (gas-phase ΔfH° = -393.51 ± 0.13 kJ/mol): https://webbook.nist.gov/cgi/cbook.cgi?Name=carbon+dioxide&cTG=on
- NIST Chemistry WebBook, Methane (gas-phase ΔfH° = -74.87 kJ/mol, Chase 1998 review): https://webbook.nist.gov/cgi/cbook.cgi?ID=C74828&Mask=8EF
- NIST Chemistry WebBook, Water (gas-phase ΔfH° = -241.826 ± 0.040 kJ/mol): https://webbook.nist.gov/cgi/cbook.cgi?Name=water&cTG=on

Reaction enthalpy uncertainty is not yet propagated. No temperature-dependent enthalpy correction is applied.


## v0.4 solar-radiation scope and limits

See `docs/SOLAR_RADIATION_V0.4.md` for the full numerical definition, data provenance, band boundaries, benchmark, and validation status. In brief, the app consumes the discrete VPL/Claire spectra at 1 AU, integrates only 0.5–2397.51 nm, and derives the global geometric mean by dividing by four. It does not call this a complete bolometric solar constant. The source PDF's 3.8 Ga partial integral (999.670976598 W m^-2) and global geometric mean (249.917744150 W m^-2) are used as an on-device benchmark. Because the downloaded spectrum itself could not be fetched in the build environment, that live source-file comparison is pending first online launch on the device.

Between reference epochs, the app uses log-linear interpolation in spectral flux at each wavelength. It refuses to extrapolate beyond the oldest available 4.4 Ga lookback spectrum, leaving the first approximately 140 Ma after adopted Earth formation explicitly without a supplied spectral estimate. The TOA calculation does not yet apply planetary albedo or atmospheric absorption.


## v0.5 atmospheric-radiation scope and limits

See `docs/ATMOSPHERIC_RADIATIVE_TRANSFER_V0.5.md` for the equations, calibration, numerical checkpoint, and limitations. In brief, the current shortwave solver applies a one-layer effective Beer–Lambert attenuation to each source wavelength band on downward and reflected upward paths. The modern calibration reproduces rounded global energy fractions of 29% reflected, 23% atmospheric absorption, and 48% surface absorption. Its effective lower-boundary reflectance folds unresolved reflection into a single parameter; it is not a literal surface albedo. The current band opacity is grey across bands and is not derived from Archaean gas inventories.

The longwave component is a single effective grey slab in radiative equilibrium. It uses the Gough bolometric luminosity fraction and holds modern effective optical parameters fixed for deep-time sensitivity. The modern surface target of 288.15 K is a calibration target, not a prediction. Pre-modern temperatures are not palaeoclimate reconstructions. Molecular line-by-line absorption (e.g. with HITRAN data), scattering, clouds, aerosols, convection, latent heat, circulation, and ocean heat storage remain future work.


## v0.7 atmospheric-weather scope

The cross-section renders cloud cover, precipitation, and discrete lightning flashes from the local weather-column state. Evaporation, saturation, condensation, cloud-water storage, rainfall, and return flow are calculated on-device. Lightning events are triggered by the model's convective/cloud electrical proxy, not by a repeating visual timer. This is a reduced single-column process demonstrator; it is not a forecast, spatial weather model, or validated palaeoclimate simulation. Thunder audio is not included.
