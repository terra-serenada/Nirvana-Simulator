#!/usr/bin/env python3
"""Structural validation for the bundled Archaean reference JSON asset."""
import json
from decimal import Decimal
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/assets/archaen_reference.json"
data = json.loads(path.read_text(encoding="utf-8"))
ledger = data["elemental_inventory_mol_atom_by_reservoir"]
expected_reservoirs = {
    "atmospheric", "ocean", "terrestrial", "crust", "hydrothermal_fluid", "magma"
}
expected_elements = {
    "H", "C", "N", "O", "Na", "Mg", "Al", "Si", "P", "S", "Cl", "K", "Ca", "Fe"
}
assert set(ledger) == expected_reservoirs, "Expected exactly six reservoir ledgers"
for reservoir, values in ledger.items():
    assert set(values) == expected_elements, f"{reservoir} does not contain all 14 elements"
    for element, amount in values.items():
        value = Decimal(amount)
        assert value >= 0, f"Negative inventory: {reservoir}/{element}"
assert data["stoichiometry_audit"]["comparisons"] == 84
assert data["stoichiometry_audit"]["species_count"] == 45
assert data["stoichiometry_audit"]["positive_water_store_count"] == 8
print("REFERENCE JSON CHECK PASSED: 6 reservoirs × 14 elements = 84 non-negative source values")
