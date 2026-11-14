#!/usr/bin/env python3
"""Compare the actual core and pinned FluffyMachines Paxel nine-slot recipes."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE_SETUP = ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersToolsSetup.java"
FLUFFY_REL = "src/main/java/io/ncbpfluffybear/fluffymachines/FluffyItemSetup.java"
TOKEN = re.compile(r"SlimefunItems\.([A-Z_0-9]+)|new ItemStack\(Material\.([A-Z_0-9]+)\)")


def read_recipe(path: Path, marker: str) -> list[str]:
    text = path.read_text(encoding="utf-8")
    start = text.index(marker)
    end = text.index(".register(plugin);", start)
    registration = text[start:end]
    assert "RecipeType.ENHANCED_CRAFTING_TABLE" in registration, path
    assert "new ItemStack[] {" in registration, path
    recipe = registration[registration.index("new ItemStack[] {"):]
    slots = [slimefun or vanilla for slimefun, vanilla in TOKEN.findall(recipe)]
    assert len(slots) == 9, f"{path}: expected exactly nine recognized recipe ingredients, got {slots}"
    return slots


core = read_recipe(CORE_SETUP, 'SlimefunItemStack paxel = new SlimefunItemStack(')
core_source = CORE_SETUP.read_text(encoding="utf-8")
assert '"ADVENTURERS_PAXEL"' in core_source.split('SlimefunItemStack paxel = new SlimefunItemStack(', 1)[1].split('.register(plugin);', 1)[0]
expected_core = [
    "SYNTHETIC_EMERALD", "DIAMOND_SHOVEL", "SYNTHETIC_EMERALD",
    "REINFORCED_ALLOY_INGOT", "DIAMOND_AXE", "REINFORCED_ALLOY_INGOT",
    "SYNTHETIC_DIAMOND", "DIAMOND_PICKAXE", "SYNTHETIC_DIAMOND",
]
assert core == expected_core, f"Unexpected Adventurers Paxel ingredients: {core}"

if len(sys.argv) != 2:
    raise SystemExit("Usage: verify_curios_paxel_recipe.py <pinned-FluffyMachines-repository>")
fluffy_source = Path(sys.argv[1]) / FLUFFY_REL
fluffy = read_recipe(fluffy_source, "new Paxel(tools, FluffyItems.PAXEL,")
expected_fluffy = [
    "SYNTHETIC_EMERALD", "DIAMOND_PICKAXE", "SYNTHETIC_EMERALD",
    "REINFORCED_ALLOY_INGOT", "DIAMOND_AXE", "REINFORCED_ALLOY_INGOT",
    "SYNTHETIC_DIAMOND", "DIAMOND_SHOVEL", "SYNTHETIC_DIAMOND",
]
assert fluffy == expected_fluffy, f"Actual FluffyMachines Paxel recipe unexpectedly changed: {fluffy}"
assert core != fluffy, "Two addon Paxel recipes must never be identical"
differences = [index for index, (a, b) in enumerate(zip(core, fluffy)) if a != b]
assert differences == [1, 7], f"Only pickaxe and shovel positions should differ: {differences}"
print("PASS: checked pinned FluffyMachines source; both real Paxel recipes differ only in slots 1 and 7")
