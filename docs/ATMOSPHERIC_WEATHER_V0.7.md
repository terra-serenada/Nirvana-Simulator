# Terra Chemica v0.7 — Reduced atmospheric weather column

## Purpose

v0.7 replaces the v0.6 repeating weather animation with a small process model. The model state now determines whether clouds are shown, whether rain streaks are drawn, how intense those streaks are, and when a lightning flash occurs.

The computation runs locally in Kotlin on the Android device. It is intentionally light enough for a mid-range phone: one representative column, a 120-second maximum internal weather step, and no spatial grid or external runtime service.

## Coupling path

1. `EarthEvolutionModel` supplies the solar age and Gough (1981) bolometric luminosity fraction when the solar age is within the approximation's stated model domain (solar age >= 0.2 Ga).
2. `AtmosphericRadiativeTransferEngine` uses that fraction in its existing one-layer grey longwave equilibrium. The returned effective surface temperature becomes the weather-column thermal forcing.
3. The weather model uses the solar forcing and effective temperature to calculate a simple day/night heating cycle, saturation water capacity, evaporation, condensation, cloud water, rainfall, a convection index, and an electrical-charge proxy.
4. The globe renderer receives the resulting `AtmosphericWeatherState`. Cloud coverage controls cloud size/opacity; precipitation rate controls rain-streak count/opacity/speed; a newly counted lightning event triggers a visible bolt and flash.

The weather clock is deliberately presentation-accelerated and independent of the geological clock: **1 real second advances 1 model-weather hour**. This makes a short-lived weather process visible during an ordinary phone session. It does not change the Earth-age clock's `Scientific Daily` definition.

## Reduced equations

All water amounts are expressed as mm of liquid water equivalent (1 mm = 1 kg m^-2).

### Diurnal forcing

Let `L` be the adopted solar-luminosity fraction and `h` the local model hour:

\[
S_{local}=1361 L \max\left(0,\sin\left(2\pi\frac{h-6}{24}\right)\right)\quad W\,m^{-2}
\]

The representative column uses small diurnal perturbations around the existing grey-model temperature:

\[
T_s=T_{grey}+2.5\sin(2\pi(h-6)/24)
\]
\[
T_a=T_{grey}-4.0+1.5\sin(2\pi(h-6)/24)
\]

These are chosen low-cost parameterizations, not observed day/night temperature profiles.

### Saturation and relative humidity

Tetens saturation vapor pressure is used as a compact approximation:

\[
e_s=611.2\exp\left(\frac{17.67T_C}{T_C+243.5}\right)\quad Pa
\]

\[
q_s=\frac{0.622e_s}{p-0.378e_s},\quad W_{sat}=q_s\,M_{air}
\]

with fixed `p = 100000 Pa` and fixed effective air-column mass `M_air = 2500 kg m^-2`. Relative humidity is `RH = W_vapor / W_sat`, numerically bounded for stability.

### Surface evaporation

\[
E=0.14\,\mathrm{clamp}(S_{local}/1000,0,1.2)\quad mm\,h^{-1}
\]

This is an effective bulk evaporation proxy. It moves water from the surface reservoir to atmospheric vapor.

### Cloud condensation and evaporation

\[
C=\max(RH-0.76,0)\,4.5\quad mm\,h^{-1}
\]

Condensation transfers vapor to cloud liquid water. A small humidity-dependent cloud-evaporation return transfers cloud water back to vapor; all terms are capped by the amount present.

### Rainfall

When cloud liquid water exceeds 0.45 mm and the convection index exceeds 0.25:

\[
P=\mathrm{clamp}((W_c-0.45)(0.45+1.10I),0,18)\quad mm\,h^{-1}
\]

Precipitation transfers cloud liquid water back to the surface reservoir. The global surface-water reservoir is fixed at `2,650,000 mm` (a modern-ocean-equivalent reference column) for all geological ages; it is an explicit placeholder, not a reconstructed palaeo-ocean inventory.

### Convective index

Four bounded nondimensional terms are combined:

- Solar heating: `H = clamp(S_local / 950, 0, 1)`
- Moisture: `M = clamp((RH - 0.48) / 0.40, 0, 1)`
- Cloud load: `C = clamp(W_c / 2.8, 0, 1)`
- Thermal contrast: `T = clamp((T_s - T_a - 1.5) / 6.5, 0, 1)`

\[
I=\mathrm{clamp}(0.35H+0.35M+0.20C+0.10T,0,1)
\]

This is **not CAPE** and does not resolve vertical profiles, entrainment, or a buoyancy equation.

### Lightning-event proxy

A bounded cloud-cover proxy is formed from humidity and cloud liquid water. When `I >= 0.62` and the cloud-cover proxy is at least `0.25`, a nondimensional electrical-charge accumulator increases according to:

\[
\Delta Q=\Delta t_h\max(0,I\,C_{cover}-0.18)
\]

A lightning event is emitted when the accumulator reaches `0.10`; it is then reduced to a small residual value. Otherwise it decays slowly. The event count drives a short visible lightning flash in the renderer.

This is an event trigger for the visual demonstrator, not a physical electric-field, charge-separation, or discharge-current calculation. No thunder audio is included.

## Conservation and tests

The engine transfers water between exactly three stores:

- surface liquid-water reservoir,
- atmospheric vapor column,
- cloud liquid-water column.

Evaporation, condensation, cloud evaporation, and precipitation do not create or destroy water. `CoreCheck.kt` runs modern and 3.8 Ga forcing tests for rainfall, lightning events, and per-step water-budget closure.

The checked modern five-day weather-clock sample produced approximately 8.59 mm accumulated rain and one lightning event; the 3.8 Ga sample under the adopted grey-sensitivity forcing produced approximately 5.59 mm and six events. These are **unit-test outputs for the chosen parameterization**, not estimates of actual ancient rainfall or lightning frequency.

The latest core validation was run with Kotlin 1.9 via `bash tools/check_core.sh`; all core checks passed, including the new weather tests. The Android APK/UI build was not run because this environment does not contain the Android SDK or Gradle Android build toolchain.

## Scope boundaries

- One representative column only; no global latitude/longitude grid, storm tracks, or spatially varying precipitation.
- The inherited surface temperature is from the v0.5 one-layer grey model, whose optical properties are held at their modern effective calibration. It is not a palaeoclimate reconstruction.
- Fixed modern-ocean-equivalent surface-water reservoir and fixed air-column mass are held across all eras.
- No explicit cloud droplet size distribution, ice phase, aerosol activation, collision-coalescence, freezing/melting, pressure evolution, topography, wind field, or ocean circulation.
- No atmospheric latent-heat feedback is returned to the radiative solver; the column is coupled one-way from radiative temperature to weather forcing.
- Lightning uses a nondimensional proxy, not observed rates or a resolved electrification model.
- Weather forcing is intentionally unavailable while the adopted Gough luminosity approximation is outside its validity domain (solar age below 0.2 Ga). The app shows that limitation rather than inventing an early-Sun forcing.

The purpose of v0.7 is to make precipitation and lightning emerge from a transparent, testable local process chain, while clearly identifying the missing physics required before treating outputs as scientific climate predictions.
