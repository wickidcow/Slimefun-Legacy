#!/usr/bin/env python3
"""Guard distinct Adventurer's Curios and FluffyMachines Paxel crafting recipes."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / "src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/AdventurersToolsSetup.java").read_text()
start = source.index('SlimefunItemStack paxel = new SlimefunItemStack(')
end = source.index('.register(plugin);', start)
registration = source[start:end]
assert '"ADVENTURERS_PAXEL"' in registration
assert 'RecipeType.ENHANCED_CRAFTING_TABLE' in registration
recipe = registration[registration.index('new ItemStack[] {'):]
slots = re.findall(r'SlimefunItems\.([A-Z_0-9]+)|new ItemStack\(Material\.([A-Z_0-9]+)\)', recipe)
slots = [sf or vanilla for sf, vanilla in slots]
expected = [
    'SYNTHETIC_EMERALD', 'DIAMOND_SHOVEL', 'SYNTHETIC_EMERALD',
    'REINFORCED_ALLOY_INGOT', 'DIAMOND_AXE', 'REINFORCED_ALLOY_INGOT',
    'SYNTHETIC_DIAMOND', 'DIAMOND_PICKAXE', 'SYNTHETIC_DIAMOND',
]
assert slots == expected, f'Unexpected Adventurers Paxel ingredients: {slots}'
fluffy_original = expected.copy()
fluffy_original[1], fluffy_original[7] = fluffy_original[7], fluffy_original[1]
assert slots != fluffy_original, 'Adventurers Paxel must not duplicate FluffyMachines Paxel recipe'
print('PASS: Adventurers Paxel recipe has distinct pickaxe/shovel slots and unchanged other ingredients')
