# Changelog

## 0.8.0 — Archaean cutaway scene and slower geological viewing

- Replaced the main decorative globe scene with a 2D cross-section view showing the early atmosphere, ocean, basaltic crust, mantle, and core in one mobile-friendly scene.
- Added animated cloud drift driven by the current model cloud fraction and liquid-water state; faint cloud wisps remain visible when cloud fraction is low.
- Kept rain tied to positive precipitation rate and lightning flashes tied to new model lightning events.
- Added slow explanatory mantle-convection arrows; these are explicitly visual cues, not a geodynamic solver.
- On first launch/upgrade, opens at the Archaean 3.8 Ga lookback anchor (740 Ma since formation), paused for inspection.
- Added a one-tap return to the Archaean 3.8 Ga anchor.
- Preserved Scientific Daily at exactly one simulated day per real second and the independent weather clock at one weather hour per real second.
- Slowed Auto Journey from 28 to 150 seconds per milestone segment and extended milestone holds from 4 to 12 seconds.
- Updated the core timing regression check and Android package version to 0.8.0.
- Retained the scientific limitation: atmospheric weather is a single representative column; the cutaway is schematic and is not a reconstructed Archaean climate/paleogeography.

## 0.7.0 — Model-driven water cycle and convective lightning

- Replaced the purely decorative weather overlay with a local atmospheric-column model.
- Coupled the weather column to the existing Gough solar-luminosity fraction and one-layer grey radiative-equilibrium surface temperature.
- Added diurnal surface heating, saturation-vapor capacity, evaporation, condensation, cloud-water storage, precipitation, and return flow to the surface reservoir.
- Added a convective-instability proxy and an electrical-charge accumulator; lightning is emitted only when moist cloud and convective thresholds are met.
- Connected cloud coverage, rain rate, and lightning-event count to the globe renderer; rain intensity changes the visible rain streaks and cloud shading.
- Added on-screen relative humidity, cloud fraction, precipitable water, cloud liquid water, rainfall rate, convection index, lightning count, and water-budget residual diagnostics.
- Added model checks for modern and 3.8 Ga forcing, rainfall, lightning events, and water conservation.
- Documented the single-column scope, fixed ocean-equivalent water reservoir, accelerated weather clock, and unsupported young-Sun interval.
- Bumped Android package version to 0.7.0.

## 0.5.0 — Effective atmospheric radiative-transfer prototype

- Added band-resolved shortwave absorption-only transfer with Beer–Lambert attenuation on downward and reflected upward passes.
- Added explicit per-layer/per-band absorption and energy residual reporting.
- Added a modern effective global-mean calibration reproducing the rounded 29% reflected, 23% atmospheric absorption, and 48% surface absorption partition.
- Added one-layer grey longwave radiative-equilibrium calculations using the Stefan–Boltzmann law.
- Calibrated the modern effective grey layer to 288.15 K; the atmospheric temperature is explicitly an effective slab quantity.
- Connected the solar-luminosity fraction to bolometric TSI scaling while preserving the existing young-Sun validity gap.
- Added app diagnostics for partial-spectrum energy pathways and bolometric grey-equilibrium sensitivity.
- Labelled pre-modern temperatures as a fixed-modern-optics solar-only sensitivity, not a palaeoclimate reconstruction.
- Documented omissions: scattering, clouds, aerosols, molecular line absorption, non-radiative heat fluxes, ocean heat storage, and circulation.
- Bumped Android package version to 0.5.0.

## 0.4.0 — Solar spectrum and TOA radiation diagnostics

- Added VPL/Claire two-column spectral parser and cgs-to-SI unit conversion.
- Added boundary-aware trapezoidal integration for all eight PDF-defined wavelength bands over 0.5–2397.51 nm.
- Added exact snapshot selection and log-flux interpolation between available spectral anchors; no extrapolation is allowed.
- Added top-of-atmosphere partial-band irradiance at 1 AU and the global geometric mean F/4.
- Added optional albedo arithmetic; no absorbed/reflected value is reported until albedo is provided.
- Added the 3.8 Ga PDF benchmark table and on-device comparison with per-band tolerance.
- Separated spectrum download and calculation executors; added checksum-validation cache and a three-spectrum memory cache.
- Added UI reporting of partial spectral irradiance and UV/visible/NIR contributions.
- Fixed Auto Journey timing to use fixed-duration linear segments and milestone holds; it no longer asymptotically slows before reaching each milestone.
- Preserved exact 1 s = 1 day Scientific Daily timing.
- Bumped Android package version to 0.4.0.
- Kept atmospheric absorption, scattering, clouds, aerosols, greenhouse transfer, OLR, and surface-temperature response explicitly outside v0.4.

## 0.3.0 — Reaction and energy bookkeeping foundation

- Added balanced reaction definitions with exact-decimal stoichiometric coefficients.
- Added independent elemental and formal-charge balance validation.
- Added Archaean molecular/ionic working-state construction from the six-reservoir dataset.
- Added transactional reaction execution with reagent availability checks and immutable state updates.
- Added per-reaction elemental, charge, and enthalpy/thermal-energy audits.
- Added four balanced starter reactions; only atmospheric CO2 methanation has a sourced standard-state enthalpy parameter (-165.012 kJ per reaction as written, 298.15 K and 1 bar).
- Added explicit safeguards: no reaction kinetics, equilibrium constants, or automatic reaction advancement are inferred.
- Added UI status for the current reaction network and changed package version to 0.3.0.

## 0.2.0 — Elemental and stoichiometric accounting

- Added 14-element ledger across six reservoirs.
- Added formula parser and source inventory reconciliation.
- Added conservative element-transfer engine and exact decimal checks.

## 0.1.0 — Earth evolution foundation

- Added Earth evolution clock and timeline presentation.
- Added solar spectral anchors and Archaean reference snapshot.

## v0.6.0 — Atmospheric weather visualization

- Added animated cloud, rain streaks, and periodic lightning bolt/flash to the globe.
- Added CUACA ON/OFF control; animation is rendered locally on the Android device.
- Clearly marked weather effects as visual-only, not yet coupled to physical atmospheric calculations.
