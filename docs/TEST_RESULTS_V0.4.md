# Terra Chemica v0.4 — test report

## Result

`bash tools/check_core.sh` completed successfully.

### Passing core checks

- Auto Journey reaches a milestone in its fixed 28 s segment, holds for the configured interval, and then continues linearly; Scientific Daily exact rate remains separately tested.
- VPL two-column sample parser accepts the published header and converts `erg s-1 cm-2 nm-1` to `W m-2 nm-1` using factor `1e-3`.
- Trapezoidal integration interpolates exact requested band boundaries rather than discarding partial edge intervals.
- Paleotime spectrum interpolation uses log-flux in time and handles different native wavelength grids by resampling onto the older snapshot grid over the shared domain.
- Spectral interpolation refuses extrapolation beyond the two reference anchors.
- Eight wavelength bands cover the entire PDF benchmark interval without gaps or overlaps in their boundaries.
- TOA global geometric mean equals integrated incoming spectral irradiance divided by four.
- Reflected/absorbed diagnostics remain unavailable when albedo is not supplied; optional albedo arithmetic is tested separately.
- The eight printed 3.8 Ga benchmark band values sum to 999.670976598 W m^-2 and the /4 value agrees with 249.917744150 W m^-2 after printed precision.
- Existing v0.1–v0.3 clock, reference JSON, stoichiometry, charge, reaction, atom-conservation, and energy-bookkeeping checks continue to pass.
- Solar benchmark JSON schema and contiguous wavelength boundaries pass Python validation.

### Source-code compilation check

All app Kotlin sources, including the changed Android activity and repository, were passed through `kotlinc` with lightweight local Android API stubs to detect Kotlin syntax/type errors. This is not a substitute for compiling against the real Android SDK and does not verify device runtime behavior.

### Not yet verified in this environment

- The build container has no Android SDK, Gradle executable, or outbound DNS/network access. Therefore no APK was built and the Zenodo spectrum files could not be downloaded here.
- The live `Sun3.8Ga.txt` 3.8 Ga PDF benchmark comparison is implemented and runs after download on the phone, but remains pending until first online launch.
- No runtime/performance test has been performed on the user's Galaxy A15 4G.

## Current scientific boundary

v0.4 reports the incoming spectral irradiance integrated over 0.5–2397.51 nm at 1 AU and its geometric global mean. It is a partial spectral-domain integral, not a complete bolometric TSI. No albedo, atmospheric absorption, scattering, cloud/aerosol forcing, outgoing longwave radiation, or temperature response is assumed by default.
