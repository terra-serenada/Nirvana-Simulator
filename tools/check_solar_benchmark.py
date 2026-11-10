import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "app/src/main/assets/solar_spectrum_benchmark.json"
data = json.loads(path.read_text(encoding="utf-8"))
assert data["lookback_ga"] == 3.8
assert data["wavelength_domain_nm"] == [0.5, 2397.51]
assert data["spectral_samples"] == 26150
bands = data["bands"]
assert len(bands) == 8
assert [b["id"] for b in bands] == ["XUV", "EUV", "FUV", "UVC", "UVB", "UVA", "VISIBLE", "NIR"]
for left, right in zip(bands, bands[1:]):
    assert abs(left["maximum_nm"] - right["minimum_nm"]) < 1e-12
assert bands[0]["minimum_nm"] == data["wavelength_domain_nm"][0]
assert bands[-1]["maximum_nm"] == data["wavelength_domain_nm"][1]
assert abs(sum(b["expected_w_m2"] for b in bands) - data["expected_partial_integral_w_m2"]) < 1e-9
assert abs(data["expected_partial_integral_w_m2"] / 4.0 - data["expected_global_geometric_mean_w_m2"]) < 1e-9
print("SOLAR PDF BENCHMARK JSON CHECK PASSED: 8 contiguous source bands; partial total and F/4 consistent")
