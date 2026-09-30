from pathlib import Path
import gzip, hashlib, json


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


payload = b''.join(Path(f'.audit/upgrade-fixture.part{i}').read_bytes() for i in range(1, 5))
assert blob(payload) == 'd1457f4f8ccca1c021e96bb2af01de33372e2dd8'
entries = json.loads(gzip.decompress(payload))
assert len(entries) == 5
for entry in entries:
    path = Path(entry['path'])
    assert not path.is_absolute() and '..' not in path.parts and not path.exists()
    assert str(path).startswith(('compatibility/upgrade-fixture/', 'scripts/old_world_upgrade.py', 'scripts/test_old_world_upgrade.py'))
    assert blob(entry['content'].encode()) == entry['sha'], path
for entry in entries:
    path = Path(entry['path'])
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(entry['content'])

probe = Path('compatibility/upgrade-fixture/LegacyUpgradeProbe.java')
text = probe.read_text()
old = 'ItemStack restored = ItemStackDataCodec.deserialize(original);'
assert text.count(old) == 1
text = text.replace(old, 'ItemStack restored = readStoredFixture(original, format);')
anchor = '    private static String component(Component value) {'
helper = '''    @SuppressWarnings("deprecation") // Exercise the old String-facing API on both old and new cores.
    private static ItemStack readStoredFixture(byte[] bytes, String format) throws Exception {
        return format.equals("text")
                ? com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils.deserializeItemStack(
                        new String(bytes, StandardCharsets.US_ASCII))
                : ItemStackDataCodec.deserialize(bytes);
    }

'''
assert text.count(anchor) == 1
text = text.replace(anchor, helper + anchor)
assert blob(text.encode()) == '22827241e322b793b605c22f275123afa52f9b23'
old = '            Bukkit.getScheduler().runTaskLater(this, this::execute, 80L);'
new = '''            fixtureOwner = Bukkit.getOfflinePlayerIfCached("LegacyFixture");
            require(fixtureOwner != null && OWNER.equals(fixtureOwner.getUniqueId())
                    && "LegacyFixture".equals(fixtureOwner.getName()), "Missing synthetic owner cache entry");
            if (seed) {
                Slimefun.getDatabaseManager().getProfileDataController().getOrCreateProfileAsync(fixtureOwner)
                        .whenComplete((profile, error) -> {
                            if (error != null) finish(error);
                            else Bukkit.getScheduler().runTaskLater(this, this::execute, 80L);
                        });
            } else {
                Bukkit.getScheduler().runTaskLater(this, this::execute, 80L);
            }'''
assert text.count(old) == 1
text = text.replace(old, new)
old = '    private World world;'
assert text.count(old) == 1
text = text.replace(old, old + '\n    // Keep the named cached identity alive while the old core writes its profile.\n    private org.bukkit.OfflinePlayer fixtureOwner;')
assert blob(text.encode()) == '282053c29b330b57697b42cafe49cfca685a2ce3'
old = 'public final class LegacyUpgradeProbe extends JavaPlugin {'
assert text.count(old) == 1
text = text.replace(old, 'public final class LegacyUpgradeProbe extends JavaPlugin implements io.github.thebusybiscuit.slimefun4.api.SlimefunAddon {')
anchor = '            require(BlockMenuPreset.getPreset(BLOCK_ID) == null, "Fixture preset already registered");'
assert text.count(anchor) == 1
text = text.replace(anchor, '            registerFixtureItems();\n' + anchor)
anchor = '    private Location location(int x) { return new Location(world, x, 80, 0); }'
helper = '''    @Override
    public JavaPlugin getJavaPlugin() { return this; }
    @Override
    public String getBugTrackerURL() { return null; }

    private void registerFixtureItems() {
        require(SlimefunItem.getById(BLOCK_ID) == null && SlimefunItem.getById(UNIVERSAL_ID) == null,
                "Fixture item IDs already registered");
        var group = new io.github.thebusybiscuit.slimefun4.api.items.ItemGroup(
                key("legacyupgradeprobe:fixtures"), new ItemStack(Material.CHEST));
        new SlimefunItem(group, new io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack(
                BLOCK_ID, new ItemStack(Material.CHEST)),
                io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.NULL, new ItemStack[9]).register(this);
        new UniversalFixtureItem(group).register(this);
    }

    private static final class UniversalFixtureItem extends SlimefunItem
            implements com.xzavier0722.mc.plugin.slimefun4.storage.controller.attributes.UniversalBlock {
        private UniversalFixtureItem(io.github.thebusybiscuit.slimefun4.api.items.ItemGroup group) {
            super(group, new io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack(
                    UNIVERSAL_ID, new ItemStack(Material.CHEST)),
                    io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.NULL, new ItemStack[9]);
        }
    }

'''
assert text.count(anchor) == 1
text = text.replace(anchor, helper + anchor)
old = '        location(0).getChunk().load();'
assert text.count(old) == 1
text = text.replace(old, '        blocks.loadChunk(location(0).getChunk(), false, true);')
assert blob(text.encode()) == '301327a79b98c5039f53253420d3141d1591b34b'
old = '        var item = registered.getItem().clone();'
new = '''        // A player inventory stores Bukkit item values, not the internal definition subclass.
        var item = new ItemStack(registered.getItem());
        require(fingerprint(registered.getItem()).equals(fingerprint(item)), "Fixture copy changed old item metadata");'''
assert text.count(old) == 1
text = text.replace(old, new)
old = '''                ItemStack restored = readStoredFixture(original, format);
                compare("items." + i, fingerprint(restored));'''
new = '''                try {
                    ItemStack restored = readStoredFixture(original, format);
                    compare("items." + i, fingerprint(restored));
                } catch (Exception failure) {
                    throw new IllegalStateException("Could not verify old item-" + i + "." + format, failure);
                }'''
assert text.count(old) == 1
text = text.replace(old, new)
assert blob(text.encode()) == '7dd58d0693db69a7a30f26365e31401d8a04dc39'
probe.write_text(text)

runner = Path('scripts/old_world_upgrade.py')
text = runner.read_text()
for old, new in (
    ('(work / "plugins" / "Slimefun").rglob("*.db")', '(work / "data-storage" / "Slimefun").rglob("*.db")'),
    ('{"plugins", "upgrade-world",', '{"plugins", "data-storage", "upgrade-world",'),
    ('for folder in ("plugins", "upgrade-world",', 'for folder in ("plugins", "data-storage", "upgrade-world",'),
):
    assert text.count(old) == 1
    text = text.replace(old, new)
old = 'def configure(work: Path) -> None:\n'
new = '''def configure(work: Path) -> None:
    # A generated offline account lets the old core create its real parent profile.
    # No live account lookup, joined player or direct SQL insertion is required.
    write_json(work / "usercache.json", [{"name": "LegacyFixture",
               "uuid": "09000000-0000-4000-8000-000000000001", "expiresOn": "2099-01-01 00:00:00 +0000"}])
'''
assert text.count(old) == 1
text = text.replace(old, new)
assert blob(text.encode()) == '8b9a29e12ab64f5a8a867bb76ef182d9926c9224'
runner.write_text(text)
tests = Path('scripts/test_old_world_upgrade.py')
text = tests.read_text()
old = "root / 'plugins' / 'Slimefun' / 'fixture.db'"
assert text.count(old) == 1
text = text.replace(old, "root / 'data-storage' / 'Slimefun' / 'fixture.db'")
assert blob(text.encode()) == 'd3313d2f4218f6df05dcb8a4dfe5c2b5a1baa48a'
tests.write_text(text)
for entry in entries:
    path = Path(entry['path'])
    print('REVIEWED_UPGRADE_FILE', blob(path.read_bytes()), path)
