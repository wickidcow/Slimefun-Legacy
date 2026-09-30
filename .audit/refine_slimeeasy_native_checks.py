from pathlib import Path
import hashlib, json


def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()


name = 'src/cargoRegression/java/top/maplex/slimeEasy/regression/NativeRuntimeRegression.java'
path = Path('addon') / name
assert blob(path.read_bytes()) == '7d2d11593db22eca6ace544fb78d6647f541f74e'
text = path.read_text()
text = text.replace('import top.maplex.slimeEasy.machine.butcher.FakePlayerFactory;', 'import top.maplex.slimeEasy.machine.butcher.FakePlayerFactory;\nimport top.maplex.slimeEasy.machine.butcher.ButcherLogic;')
text = text.replace('        fake.setOp(false);', '        plugin.getLogger().info("SLIMEEASY_MACHINE_PROFILE_NAMES current=" + fake.getName()\n                + " offline=" + Bukkit.getOfflinePlayer(fake.getUniqueId()).getName());\n        fake.setOp(false);')
old = '''            fake.attack(cow);
            check(cow.getHealth() < health, "Native player attack must cause actual damage");'''
new = '''            // Exercise the real machine implementation, not Player.attack(), which
            // SlimeEasy's butcher does not call and requires a connected client.
            var hit = ButcherLogic.INSTANCE.performSweep(fake, position, 3, 2, 2, 4.0, null, null);
            check(hit.contains(cow), "Actual butcher sweep must select its target");
            check(Math.abs(cow.getHealth() - (health - 4.0)) < 0.0001,
                    "Configured butcher damage must remain exactly four health points");
            check(cow.getPersistentDataContainer().has(ButcherLogic.INSTANCE.getKEY_KILLER(),
                    org.bukkit.persistence.PersistentDataType.STRING), "Existing machine kill marker retained");'''
assert text.count(old) == 1
text = text.replace(old, new)
path.write_text(text)
manifest = Path('audit-evidence/source-manifest.json')
data = json.loads(manifest.read_text())
data[name] = blob(path.read_bytes())
manifest.write_text(json.dumps(data, indent=2))
print('Actual production butcher path fixture:', data[name])
