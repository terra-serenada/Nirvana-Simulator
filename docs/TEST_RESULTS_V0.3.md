# Terra Chemica v0.3 — Core test results

Test command: `bash tools/check_core.sh`

Environment: Kotlin/JVM compiler 1.9.0, OpenJDK 21. Android SDK and Gradle are not installed in the build environment, so these checks cover the pure Kotlin core and data asset validation, not an Android APK build or device runtime.

## Results

- Reaction catalog: 4 / 4 reactions pass elemental and formal-charge balance.
- Thermochemical catalog: 1 / 4 reactions has a sourced standard-state reaction enthalpy.
- Kinetic catalog: 0 / 4 reactions has a configured kinetic law; no reaction is automatically advanced.
- Reaction test extent: 1.0 × 10^12 mol for atmospheric CO2 methanation.
- Reaction test atom residual: exactly zero for all formula-derived elements, both per reservoir and across the system.
- Reaction test charge residual: exactly zero mol-charge.
- Reaction test energy residual: exactly 0.000 kJ under the declared adiabatic bookkeeping assumption.
- Incomplete-enthalpy reaction: complete-energy execution is rejected by default; permissive execution is explicitly marked incomplete.
- Oversized extent: rejected without mutating the original checkpoint.
- Reference elemental ledger: 14 elements × 6 reservoirs = 84 entries.
- Source reconciliation: 45 species + 8 water sub-stores vs. 84 ledger values; maximum relative residual approximately 6.6690949108 × 10^-10 (within the configured 10^-9 tolerance).
- Solar/time checks: Hadean start, 3.8 Ga Archaean solar-spectrum anchor, 14 h rotation reference at the anchor, modern spectrum anchor, and Scientific Daily clock test pass.
- Reference JSON: 6 reservoirs × 14 non-negative source values pass.

## Interpretation

Passing these tests verifies stoichiometric bookkeeping and transaction invariants. It does not validate reaction rates, atmospheric conditions, reaction equilibrium, paleoclimate, or a coupled Earth-system trajectory.
