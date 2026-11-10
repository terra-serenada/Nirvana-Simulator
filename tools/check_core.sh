#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build-core-check.jar"
kotlinc \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/EvolutionClock.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/EarthEvolutionModel.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ConservativeLedger.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ElementalConservationEngine.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/data/ArchaeanReference.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ArchaeanSnapshotFactory.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ChemicalFormulaParser.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ArchaeanStoichiometryAudit.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ChemicalReactionEngine.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ArchaeanChemicalStateFactory.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/ReactionNetworkCatalog.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/SolarRadiationEngine.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/AtmosphericRadiativeTransferEngine.kt" \
  "$ROOT/app/src/main/java/org/terrachemica/app/core/AtmosphericWeatherEngine.kt" \
  "$ROOT/tools/CoreCheck.kt" \
  -include-runtime -d "$OUT"
java -jar "$OUT"
python3 "$ROOT/tools/check_reference_json.py"
python3 "$ROOT/tools/check_solar_benchmark.py"
rm -f "$OUT"
