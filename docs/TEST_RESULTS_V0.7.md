# v0.7 core test results

**Date:** 2026-11-10  
**Runtime:** Kotlin 1.9.0, OpenJDK 21  
**Command:** `bash tools/check_core.sh`

## Result

All pure-Kotlin core checks passed:

- Geological clock and Auto Journey milestone timing.
- Solar luminosity/spectrum interpolation and no-extrapolation safeguards.
- TOA geometry and shortwave energy closure.
- Grey longwave radiative-equilibrium closure.
- Elemental ledger, stoichiometry, reaction balance, and transactional conservation.
- Weather-column rain generation, lightning-event generation, and water-budget closure under modern and 3.8 Ga grey-sensitivity forcing.
- JSON reference and solar-benchmark checks.

## Weather test samples

The weather test advances a five-day weather-clock sample at hourly increments. These numbers validate deterministic code paths and conservation only; they are not observed weather or palaeoclimate estimates.

| Forcing case | Accumulated rain | Lightning events | Last-step water residual |
|---|---:|---:|---:|
| Modern effective grey calibration | 8.593145 mm | 1 | -4.6566 × 10^-10 mm |
| 3.8 Ga solar/grey sensitivity | 5.590529 mm | 6 | 0.0 mm |

The current engine uses a 120-second maximum internal weather step. The production UI maps one real second to one model-weather hour.

## Android build status

The core logic check does not build the Android interface. This environment contains the Kotlin command-line compiler but does not contain the Android SDK (`android.jar`, platform 35, or build tools) or Gradle. Therefore no APK has been produced or installed, and the Android rendering has not been runtime-tested on the Samsung Galaxy A15 4G. The project package version is set to 0.7.0.
