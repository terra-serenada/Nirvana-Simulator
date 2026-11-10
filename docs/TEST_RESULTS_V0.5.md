# Terra Chemica v0.5 — Core test results

**Run date:** 2026-11-10  
**Test command:** `bash tools/check_core.sh`  
**Environment:** Kotlin/JVM core compilation; Android UI not built in this environment.

## Result

`CORE CHECK PASSED`

## v0.5-specific checks

- All 8 source-defined solar bands are passed through the shortwave absorption-only column.
- Modern effective calibration reproduces the configured 23% atmosphere / 48% surface / 29% reflected partition.
- Per-band shortwave energy residuals are within `1e-10 W m-2` in the synthetic/PDF benchmark test.
- Aggregate shortwave energy closure residual: approximately `-1.42e-14 W m-2`.
- Modern grey longwave reference reproduces a surface target of `288.15 K`.
- Modern global incoming solar flux is `340.25 W m-2` for TSI `1361 W m-2`.
- Modern outgoing longwave is `241.5775 W m-2`.
- Longwave top-of-atmosphere residual: approximately `-5.68e-14 W m-2`.
- 3.8 Ga Gough luminosity sensitivity returns a lower effective equilibrium temperature than modern, while remaining explicitly labelled as a fixed-modern-optics sensitivity experiment.
- The young-Sun interval remains unavailable where the Gough approximation is disabled.

## Retained prior checks

- Auto Journey fixed-duration segments and milestone holds.
- Scientific Daily exact 1 real second = 1 simulated day.
- Archaean reference ledger: 14 elements × 6 reservoirs = 84 entries.
- Formula audit: 45 species + 8 H2O stores reconcile to 84 ledger values within the source-rounding tolerance.
- Conservative element transfer and invalid-batch rejection.
- Reaction atom/charge balance, reagent checks, and reaction-energy bookkeeping.
- Solar spectral unit conversion, wavelength integration, time interpolation, and refusal to extrapolate.
- Archaean PDF 3.8 Ga partial-spectrum benchmark constants.
- JSON asset structure checks.

## Not tested here

- Android Gradle build or APK generation.
- Rendering and lifecycle on a physical Galaxy A15 4G.
- Actual download and checksum verification of the external solar spectrum files on the device.
- Molecular line-by-line radiative transfer, cloud/aerosol scattering, convection, latent heat, or a validated palaeoclimate reconstruction.
