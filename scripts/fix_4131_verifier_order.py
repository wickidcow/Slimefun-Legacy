#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
path = root / "scripts/verify_adventurers_curios.py"
text = path.read_text(encoding="utf-8")

block = '''        energy = read(root, files["energy"])
        for token in (
            'ELECTRIC_MODE_KEY = "beacon_plus_electric_mode"', "energy-charge", "getDemand(",
            "consumePulse(", "hasOperationalPower(", "Activator",
        ):
            req(token in energy, f"Resonance Beacon energy invariant missing: {token}", failures)
        req("BeaconPlusEnergy.consumePulse(block, data, tiers)" in runtime,
            "Resonance Beacon runtime must pay electric cost once per pulse", failures)
        req("getPotentialActiveTiers" in runtime,
            "Electric operation must preserve configured tiers while energy-gating active tiers", failures)

'''

runtime_anchor = '''        runtime = read(root, files["runtime"])
        runtime_effects = read(root, files["runtime_effects"])
'''

if block not in text:
    raise SystemExit("Expected 4.1.31 energy verification block was not found")

# Remove it from wherever the staging script placed it, then put it after runtime is loaded.
text = text.replace(block, "", 1)
if runtime_anchor not in text:
    raise SystemExit("Runtime verifier anchor was not found")
text = text.replace(runtime_anchor, runtime_anchor + block, 1)
path.write_text(text, encoding="utf-8")
print("4.1.31 Curios energy verifier ordering corrected")
