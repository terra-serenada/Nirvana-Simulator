# v0.4 — Solar spectral integration and TOA radiation diagnostics

## Scope delivered

v0.4 adds a spectral-data and top-of-atmosphere (TOA) diagnostic layer. It does **not** yet solve atmospheric radiative transfer, surface temperature, climate, or atmospheric absorption.

### Spectral source and file handling

The app downloads seven VPL/Claire spectral snapshots from Zenodo record 17981836:

- Sun4.4Ga.txt
- Sun3.8Ga.txt
- Sun2.7Ga.txt
- Sun2.4Ga.txt
- Sun1.8Ga.txt
- Sun0.6Ga.txt
- SunModern.txt

The Zenodo record identifies these as past/modern solar spectra for planetary modelling and attributes the past snapshots to Claire et al. (2012). The file header identifies columns as wavelength in nm and flux in erg s^-1 cm^-2 nm^-1, at 1 AU. Each file is checksum-validated against the MD5 listed in the Zenodo record before parsing.

The parser converts spectral flux density using:

`1 erg s^-1 cm^-2 nm^-1 = 1e-3 W m^-2 nm^-1`.

The app keeps the source spectrum's wavelength grid. A small in-memory LRU cache retains at most three parsed snapshots; the downloaded files are stored in app-private storage. The download and calculation executors are separate so a spectrum download does not block the display's calculation requests. Checksum verification is cached to prevent repeatedly hashing several MB on each UI refresh.

### Wavelength domain and reference validation

The supplied Archaean of Arus PDF reports the 3.8 Ga spectrum over 0.5–2397.51 nm, 26,150 spectral points, unnormalized, and an integrated partial irradiance of 999.670976598 W m^-2. The PDF also lists eight band integrals and a geometric global mean of 249.917744150 W m^-2. Those values are preserved in `app/src/main/assets/solar_spectrum_benchmark.json` and the Kotlin benchmark checker.

The integration uses trapezoidal quadrature. If a requested band boundary falls between data samples, the spectrum is linearly interpolated to the boundary before integration. This prevents silently omitting the partial first/last bin.

The band boundaries match the supplied PDF:

| Band | Range (nm) |
|---|---:|
| X-ray / XUV | 0.5–10 |
| EUV | 10–121 |
| FUV | 121–200 |
| UV-C | 200–280 |
| UV-B | 280–315 |
| UV-A | 315–400 |
| Visible | 400–700 |
| NIR | 700–2397.51 |

The total is intentionally named `coveredBandIrradianceWm2`. It is **not** labelled bolometric TSI because the included file/domain is a partial wavelength interval. The source PDF explicitly says the spectrum is not normalized. The app computes the whole-sphere geometric mean as the covered-band integral divided by four. It does not invent an albedo: reflected/absorbed flux remains null until a sourced or scenario-specified Bond albedo is supplied.

### Time selection and interpolation

- At an exact source epoch, the app uses that source snapshot directly.
- Between two source epochs, each wavelength's spectral flux is interpolated linearly in log-flux with respect to lookback time. If either endpoint is exactly zero, that wavelength uses linear-flux interpolation.
- The interpolation uses the older snapshot grid and linearly resamples the newer snapshot onto that grid across the shared wavelength domain. It refuses extrapolation and requires the shared domain to cover the full benchmark interval.
- The interpolation is a Terra Chemica numerical interpolation method, not a new Claire et al. result or an independently observed spectrum.

The oldest supplied Earth-lookback anchor is 4.4 Ga. The adopted Earth-formation start is 4.54 Ga lookback, so the first approximately 140 million years of the app's Earth timeline have no direct supplied spectral anchor. The UI explicitly reports the gap rather than extrapolating a spectrum. Claire et al. describe a broader parameterization and extensions with additional uncertainties; this app uses the discrete downloadable VPL snapshots listed in the Zenodo record.

### TOA geometry and atmospheric boundary

The engine computes:

- spectral irradiance integrated over the source-covered wavelength range at 1 AU;
- each of the eight wavelength-band contributions;
- the sphere-average incoming geometric flux `F / 4`;
- reflected and absorbed global-mean flux only when an albedo is explicitly supplied.

No Bond albedo is supplied by default. No atmospheric gas absorption cross-sections, pressure-broadening, Rayleigh scattering, clouds, aerosols, surface spectral albedo, longwave opacity, greenhouse transfer, or outgoing-longwave-radiation model are active in v0.4. Accordingly, this version reports incoming solar energy at TOA and does not claim to predict atmospheric or surface temperature.

The existing Gough (1981) bolometric luminosity approximation remains a separate diagnostic for its allowed solar-age domain. It is **not multiplied into the Claire spectral snapshots**, because those snapshots already represent paleosolar spectral flux at 1 AU. This prevents double-scaling the input radiation.

### Device and performance policy

- The spectrum files are a few MB in total and are downloaded only once unless checksum verification fails.
- Up to three parsed spectra are kept in memory; raw downloaded files remain on local storage.
- Spectral display diagnostics refresh on 25 Ma timeline buckets to avoid re-integrating roughly 26,150 samples four times per second. This bucket is only a UI refresh cadence; the `Scientific Daily` geological clock remains exactly 1 real second = 1 simulated day.
- The solar spectrum integration is CPU work; the Android UI remains on the main thread and calculations/downloads use background executors.

## v0.4 validation status

Pure Kotlin tests validate:

1. cgs-to-SI spectral flux-density conversion;
2. wavelength parsing and strictly increasing wavelengths;
3. trapezoidal integration with interpolated band edges;
4. log-flux interpolation between two source snapshots;
5. rejection of time extrapolation;
6. eight bands tiling the 0.5–2397.51 nm domain;
7. `F / 4` global-mean geometry;
8. optional albedo arithmetic and absence of absorbed/reflected values when albedo is missing;
9. consistency of PDF benchmark-band values with the printed total and global mean.

The live downloaded `Sun3.8Ga.txt` file has **not** been downloaded and executed in this build environment because outbound DNS/network access is unavailable. On the Android device, after download/checksum verification, the app compares all eight computed 3.8 Ga band integrals and the total against the PDF benchmark with a 5e-4 W m^-2 absolute tolerance. That runtime source-file comparison remains pending until first online launch on the phone.

## Sources

- User-provided `Archaean_of_Arus_v0.1_Data_Mentah_Unsur_Energi_6_Reservoir.pdf`, page 3: solar spectrum domain, count, normalization status, integral, geometric mean, and eight band values.
- Zenodo record 17981836, `stellar_spectra/solar`: https://zenodo.org/records/17981836
- Claire, M. W. et al. (2012), “The evolution of solar flux from 0.1 nm to 160 μm: quantitative estimates for planetary studies,” *The Astrophysical Journal* 757, 95. https://doi.org/10.1088/0004-637X/757/1/95
