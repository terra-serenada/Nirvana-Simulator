# Terra Chemica v0.5 — Atmospheric radiative-transfer prototype

## 1. Scope

Version 0.5 adds two deliberately limited components:

1. A band-resolved, absorption-only shortwave column using Beer–Lambert attenuation on a downward pass and a reflected upward pass.
2. A single-effective-layer, grey longwave radiative-equilibrium sensitivity calculation.

This is **not** a full atmospheric radiative-transfer or climate model. The shortwave layer is wavelength-band aware in its data contract, but the current modern calibration uses the same effective optical depth in every band. The model does not separately solve scattering, clouds, aerosols, multiple reflections, molecular line absorption, convection, latent heat, atmospheric circulation, or ocean heat storage.

## 2. Solar input definitions

The v0.4 spectral files cover 0.5–2397.51 nm. Their integral is a partial spectral integral and must not be called total solar irradiance (TSI). For each band the incoming global-mean diagnostic remains:

`F_band_global = integral_band_at_1AU / 4`

This `/4` is the spherical-area average for a spherical Earth. The shortwave column transfers only this documented partial spectral domain.

A separate bolometric sensitivity uses the adopted modern TSI of 1361 W m-2, multiplied by the pre-existing Gough solar-luminosity fraction:

`TSI(age) = 1361 W m-2 * L(age)/L0`

`F_global = TSI(age)/4`

The Gough luminosity approximation remains unavailable for solar ages below 0.2 Ga; the engine returns no bolometric equilibrium in that interval rather than extrapolating.

## 3. Effective modern calibration

NASA's simplified global energy-budget explainer gives approximately 29% reflected solar energy, 23% absorbed in the atmosphere, and 48% absorbed at the surface. NASA gives a present total solar irradiance close to 1360–1361 W m-2, corresponding to about 340 W m-2 averaged over the sphere, and a global-mean surface temperature close to 15 C.

The prototype encodes the rounded calibration fractions:

- reflected fraction: `f_ref = 0.29`
- atmospheric shortwave absorbed fraction: `f_atm = 0.23`
- surface shortwave absorbed fraction: `f_sfc = 0.48`
- modern TSI: `S0 = 1361 W m-2`
- modern target surface temperature: `Ts0 = 288.15 K`
- effective cosine of zenith: `mu_eff = 0.5`

These fractions sum to one. They are rounded global-mean calibration targets, not independently resolved physical pathways.

### 3.1 Effective shortwave optical depth

The one-layer absorption-only downward transmittance `T` and lower-boundary reflectance `r` are derived to reproduce `f_sfc` and `f_ref`:

`T = (f_sfc + sqrt(f_sfc^2 + 4*f_ref))/2`

`r = 1 - f_sfc/T`

`tau_sw = -mu_eff * ln(T)`

With the configured fractions, `T` is approximately 0.829576, `r` approximately 0.421391, and total effective `tau_sw` approximately 0.093420.

The lower-boundary reflectance is **not a literal bare-ground albedo**. In this absorption-only closure it is an effective parameter that folds all unresolved reflection into one boundary. The one-layer shortwave optical depth is also an effective broadband value, not a molecular absorption cross-section.

For each source wavelength band, downward incoming flux is attenuated by `exp(-tau/mu_eff)`. The transmitted portion reaches the lower boundary; the reflected part is then attenuated on the upward pass. Absorption is recorded separately for the down and up passes. Per-band energy residual is:

`incoming - atmospheric_absorption - surface_absorption - reflected_to_space`

The current engine closes that residual to floating-point roundoff.

## 4. Effective grey longwave equilibrium

The longwave model treats the atmosphere as one effective grey slab with emissivity `epsilon`. It uses the Stefan–Boltzmann law and solves the two equilibrium equations:

`σ Ts^4 - epsilon * σ Ta^4 = F_sfc_SW`

`-epsilon * σ Ts^4 + 2*epsilon * σ Ta^4 = F_atm_SW`

where `F_sfc_SW` and `F_atm_SW` are the shortwave absorbed fluxes in the effective calibration.

The emissivity is derived once from the modern reference condition:

`epsilon = 2 - (2*F_sfc_SW,0 + F_atm_SW,0)/(σ*Ts0^4)`

The solution for a given TSI is:

`σ Ts^4 = (2*F_sfc_SW + F_atm_SW)/(2 - epsilon)`

`σ Ta^4 = (F_atm_SW + epsilon*F_sfc_SW)/(epsilon*(2 - epsilon))`

The model computes the surface, atmospheric-slab, and top-of-atmosphere residuals independently.

### 4.1 Modern reference checkpoint

Using TSI = 1361 W m-2:

- global-mean incoming solar flux: 340.25 W m-2
- reflected shortwave: 98.6725 W m-2
- atmospheric shortwave absorption: 78.2575 W m-2
- surface shortwave absorption: 163.32 W m-2
- derived effective IR emissivity: approximately 0.964241
- calibrated surface temperature: 288.15 K
- effective slab temperature: approximately 254.005 K
- outgoing longwave: 241.5775 W m-2

The modern surface temperature is a **calibration target**, not an independent prediction. The 254 K slab temperature is an effective model quantity; it is not a measured mean temperature of a literal atmospheric layer.

## 5. Use in deep time

For solar ages within the Gough approximation's stated validity window, the code scales bolometric TSI by `L/L0` and re-solves the grey equilibrium. It deliberately holds the modern effective albedo fractions and grey emissivity constant.

Therefore every pre-modern temperature is labelled:

**"Modern effective radiative properties held fixed; solar-only sensitivity, not a palaeoclimate reconstruction."**

It must not be presented as the reconstructed Hadean, Archaean, Proterozoic, or Phanerozoic surface temperature. Ancient greenhouse gas concentrations, atmospheric pressure, cloud/aerosol conditions, surface albedo, geography, ocean heat storage, and non-radiative surface fluxes are not dynamically calculated yet.

## 6. Why HITRAN / spectroscopic data are still needed

A composition-dependent atmospheric model needs wavelength-dependent absorption coefficients or cross-sections as functions of temperature and pressure, together with vertical gas profiles and pressure broadening. HITRAN is a spectroscopic-parameter database; it is not itself an atmospheric simulation engine. The current v0.5 effective optical depth must not be interpreted as having been derived from the Archaean CO2/H2/N2 inventories.

Future work should add vetted gas absorption data and a radiative-transfer solver before allowing optical depth to evolve automatically with reservoir composition.

## 7. Validation

The core test suite verifies:

- the 8 source wavelength bands are all transferred;
- shortwave downward + upward absorption and reflected escape close per band;
- the aggregate modern effective partition reproduces 23% atmosphere absorption, 48% surface absorption, and 29% reflection;
- longwave surface, slab-atmosphere, and TOA residuals close to `1e-8 W m-2`;
- the modern calibration reproduces 288.15 K and 241.5775 W m-2 OLR;
- the Archaean 3.8 Ga luminosity sensitivity is lower than the modern reference;
- the young-Sun interval remains explicitly unavailable where the Gough approximation is disabled.

These are model-internal checks and calibration checks. They do not validate a full palaeoclimate model or molecular radiative transfer.

## 8. External references

- NASA Earth Observatory, *Climate and Earth’s Energy Budget*: https://science.nasa.gov/earth/earth-observatory/climate-and-earths-energy-budget/
- NASA GSFC, *Solar Irradiance Science*: https://earth.gsfc.nasa.gov/climate/projects/solar-irradiance/science
- NASA NSSDC, *Earth Fact Sheet*: https://nssdc.gsfc.nasa.gov/planetary/factsheet/earthfact.html
- NASA, *The Earth’s Radiation Budget*: https://science.nasa.gov/ems/13_radiationbudget/
- HITRAN, *About the HITRAN Database*: https://www.hitran.org/about/
- HITRAN, *Definitions and Units*: https://www.hitran.org/docs/definitions-and-units/
