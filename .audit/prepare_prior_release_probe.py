from pathlib import Path
import hashlib

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

p = Path('scripts/prior-release-upgrade-probe/src/main/java/io/github/wickidcow/validation/PriorReleaseUpgradeProbe.java')
assert blob(p.read_bytes()) == 'c975b5fc39c6105615f3cf11b871a38a122fc922'
text = p.read_text()
changes = (
    ('11111111-2222-3333-4444-555555555555', '2bda0049-361f-3b4d-b1b6-f0ca8ea55afb'),
    ('var owner = Bukkit.getOfflinePlayer(OWNER);', 'var owner = Bukkit.getOfflinePlayer("LegacyFixture");\n        require(OWNER.equals(owner.getUniqueId()), "Offline fixture UUID did not match its name");'),
    ('private static final String BLOCK_ID = "CARGO_INPUT_NODE";', 'private static final String BLOCK_ID = "CARGO_NODE_INPUT";'),
    ('ItemStack item = definition.getItem().clone();', 'ItemStack item = ItemStack.deserializeBytes(definition.getItem().serializeAsBytes());\n            require(IDS[i].equals(Slimefun.getItemDataService().getItemData(item).orElse(null)),\n                    "Native player-item conversion lost its registered identity");'),
    ('        blockSlots = new ArrayList<>();\n        for (int slot = 0; slot < block.getBlockMenu().toInventory().getSize() && blockSlots.size() < items.size(); slot++) {\n            if (!block.getBlockMenu().getPreset().getPresetSlots().contains(slot)) blockSlots.add(slot);\n        }',
     '        // These are actual CargoInputNode filter slots, not its dynamic UI controls.\n        blockSlots = List.of(19, 20, 21, 28, 29);\n        for (int slot : blockSlots) {\n            require(slot < block.getBlockMenu().toInventory().getSize()\n                    && !block.getBlockMenu().getPreset().getPresetSlots().contains(slot)\n                    && block.getBlockMenu().getItemInSlot(slot) == null,\n                    "Expected an empty real cargo filter slot: " + slot);\n        }'),
)
for before, after in changes:
    assert text.count(before) == 1, before
    text = text.replace(before, after)
p.write_text(text)
print('TESTED_PROBE_SOURCE', blob(p.read_bytes()))
p = Path('scripts/run_prior_release_upgrade.py')
assert blob(p.read_bytes()) == 'ebb0cf8ec8b85d96dbddfdd972b3425359371ade'
text = p.read_text()
assert text.count('11111111-2222-3333-4444-555555555555') == 1
p.write_text(text.replace('11111111-2222-3333-4444-555555555555','2bda0049-361f-3b4d-b1b6-f0ca8ea55afb'))
print('TESTED_RUNNER_SOURCE', blob(p.read_bytes()))
