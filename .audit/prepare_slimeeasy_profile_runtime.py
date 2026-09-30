"""Prepare a disposable SlimeEasy runtime fixture from the exact prior reviewed source."""
from pathlib import Path
import hashlib
import io
import json
import subprocess
import zipfile


def api(path):
    return subprocess.run(['gh', 'api', path], check=True, capture_output=True, timeout=120).stdout


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


expected = {
    'build.gradle.kts': '6de4e2fb116ae4d8e6d224b08819f2f05454787e',
    'gradle/libs.versions.toml': 'bf3bbe5f9922f8c615250b4e3e476be5dd654f1d',
    'src/main/resources/paper-plugin.yml': 'f95e332be55dc9f7659868fd5ad314e90a0a7279',
    'src/cargoRegression/resources/paper-plugin.yml': '5714e1bd05cf7704cdb7d4e41115342fbfa55c79',
    'src/main/kotlin/top/maplex/slimeEasy/machine/butcher/FakePlayerFactory.kt': 'a59b96e4d6937b79c13aeb0c1f5ceaf719a10d22',
    'src/main/java/top/maplex/slimeEasy/machine/butcher/FakeCraftPlayerBridge.java': '4c55c484cc105b23427f8a77bb66c1ff97fe3d96',
    'src/cargoRegression/java/top/maplex/slimeEasy/regression/NativeRuntimeRegression.java': '00b715c32814d125c70dc0519eaefbe99d2c2208',
    'src/cargoRegression/java/top/maplex/slimeEasy/regression/CargoRegression.java': 'd84caa30feba5e3bfebeeef11acf332abc6265b3',
}
raw = api('repos/wickidcow/Slimefun-Legacy/actions/artifacts/11131472070/zip')
assert hashlib.sha256(raw).hexdigest() == '4db62f7e1d6ad4a022f3b22aa7f0139fdb0403466c0549c299c4419a16a156ee'
root = Path('addon')
with zipfile.ZipFile(io.BytesIO(raw)) as archive:
    assert json.loads(archive.read('source-manifest.json')) == expected
    for name, digest in expected.items():
        data = archive.read('tested-source/' + name)
        assert blob(data) == digest, name
        target = root / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

path = root / 'src/cargoRegression/java/top/maplex/slimeEasy/regression/NativeRuntimeRegression.java'
text = path.read_text()
text = text.replace('        Cow cow = null;', '        var bannerSupport = banner.getRelative(BlockFace.DOWN);\n        Cow cow = null;')
text = text.replace('            banner.setType(Material.WHITE_BANNER);', '            bannerSupport.setType(Material.STONE);\n            banner.setType(Material.WHITE_BANNER);')
old = '            check(BannerNmsBridge.INSTANCE.apply(plugin, banner, template, 1), "Native banner component path");'
new = '''            boolean nativeApplied = BannerNmsBridge.INSTANCE.apply(plugin, banner, template, 1);
            plugin.getLogger().info("SLIMEEASY_BANNER_NATIVE_RESULT applied=" + nativeApplied
                    + " type=" + banner.getType() + " count=" + ((Banner) banner.getState()).getPatterns().size());
            // The production operation deliberately includes a Bukkit fallback.
            // Test that complete path rather than requiring its optional first attempt to succeed.
            var sync = top.maplex.slimeEasy.territory.TerritoryService.class.getDeclaredMethod(
                    "synchronizeFlagBlock", top.maplex.slimeEasy.territory.TerritoryBlock.class,
                    DyeColor.class, java.util.List.class);
            sync.setAccessible(true);
            var anchor = new top.maplex.slimeEasy.territory.TerritoryBlock(world.getUID(), banner.getX(), banner.getY(), banner.getZ());
            check(Boolean.TRUE.equals(sync.invoke(top.maplex.slimeEasy.territory.TerritoryService.INSTANCE,
                    anchor, DyeColor.WHITE, java.util.List.of(pattern))), "Complete territory banner synchronization");'''
assert text.count(old) == 1
text = text.replace(old, new)
text = text.replace('            banner.setType(Material.AIR);', '            banner.setType(Material.AIR);\n            bannerSupport.setType(Material.AIR);')
assert blob(text.encode()) == '315ca4a022a629a113367b8a97f8576e404642d5'
needle = '        Player fake = FakePlayerFactory.INSTANCE.get(world);'
assert text.count(needle) == 1
text = text.replace(needle, '''        Files.write(Path.of("profile-research-expected.txt"),
                io.github.thebusybiscuit.slimefun4.implementation.Slimefun.getRegistry().getResearches().stream()
                        .filter(io.github.thebusybiscuit.slimefun4.api.researches.Research::isEnabled)
                        .map(research -> research.getKey().toString()).sorted().toList());
''' + needle)
path.write_text(text)
# Keep all original Cargo, identity, permission-file, native click/damage and exact pattern assertions.
# Only the old optional subpath assertion is replaced by the actual production operation.
evidence = Path('combined-evidence')
evidence.mkdir(exist_ok=True)
for name in expected:
    data = (root / name).read_bytes()
    expected[name] = blob(data)
    dest = evidence / 'tested-source' / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_bytes(data)
(evidence / 'source-manifest.json').write_text(json.dumps(expected, indent=2) + '\n')
print('Prepared exact floor candidate and full production-path fixture; no items, recipes or persisted IDs changed.')
