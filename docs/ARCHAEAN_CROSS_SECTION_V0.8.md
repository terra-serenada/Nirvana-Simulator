# Archaean cross-section scene v0.8

## Purpose

The main mobile scene is a conceptual 2D cutaway rather than a globe. It places an atmospheric region above an ocean/land boundary and schematic basaltic crust, mantle, and core layers below it. This is an explanatory renderer intended to make the simulated Earth system legible on a phone display.

## Time behavior

On first launch after v0.8 is installed, the app starts at the Archaean reference anchor: 3.800 Ga lookback, represented by 740 Ma since the adopted 4.540 Ga Earth-formation epoch. The clock is paused by default. A dedicated control restores this epoch. Scientific Daily remains 1 real second per simulated day. The weather clock remains separate at 1 real second per model-weather hour. Auto Journey is a presentation accelerator with 150 seconds of travel per milestone segment and 12 seconds of hold at each milestone.

## Weather rendering

Cloud drift uses the live weather state: cloud fraction, cloud-liquid-water column, convection index, and precipitation rate. A thin visual wisp remains drawn at low cloud fraction so a small nonzero coverage does not disappear from the scene. Rain streaks are drawn only when the model precipitation rate is positive. Lightning is rendered as a short flash only when the model lightning-event counter increases. No recurring lightning timer creates events independently of the model.

The atmospheric model remains a single representative column. The rendering projects that column into the cutaway as a visual scene; it is not a spatially resolved atmosphere or global weather map. Cloud geometry and drift are schematic, not a spatial reconstruction.

## Geology depiction limits

The ocean, primitive basaltic crust, mantle, and core are drawn as labeled explanatory layers. Mantle arrows are slow visual cues only. No mantle convection solver, core heat-transfer model, crustal thickness evolution, plate tectonics, Archaean coastline, or paleogeography reconstruction is implemented by this renderer. It must not be read as quantitative geophysical output.

## Build and validation status

The pure Kotlin core test suite covers clock behavior and the existing chemistry, radiation, and single-column weather calculations. It does not compile or instrument the Android Canvas renderer. A complete Android APK build still requires Android SDK Platform 35 and Gradle 8.9 or a compatible installed toolchain, or the repository's GitHub Actions workflow.
