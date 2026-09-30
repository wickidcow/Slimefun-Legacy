from pathlib import Path
import hashlib
import json

base = Path('addon')
inputs = Path('.audit')

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

expected = {
    'build.gradle.kts': ('eae72f5f4726bdb9cebd486079a98441eac0555f', '6de4e2fb116ae4d8e6d224b08819f2f05454787e'),
    'gradle/libs.versions.toml': ('4c997ace2ead696ab0f447958a958fdd54ff9743', 'bf3bbe5f9922f8c615250b4e3e476be5dd654f1d'),
    'src/main/resources/paper-plugin.yml': ('8cdc30eba2944a6daae6671e24e11d9bbd324e1b', 'f95e332be55dc9f7659868fd5ad314e90a0a7279'),
    'src/cargoRegression/resources/paper-plugin.yml': ('2aac551dfedad4c454f88d4ccdf7c4d05a271fe5', '5714e1bd05cf7704cdb7d4e41115342fbfa55c79'),
    'src/main/kotlin/top/maplex/slimeEasy/machine/butcher/FakePlayerFactory.kt': ('4d5400ce376a628bc92c2f0e3b13055b89ecbdec', 'a59b96e4d6937b79c13aeb0c1f5ceaf719a10d22'),
    'src/main/java/top/maplex/slimeEasy/machine/butcher/FakeCraftPlayerBridge.java': (None, '4c55c484cc105b23427f8a77bb66c1ff97fe3d96'),
    'src/cargoRegression/java/top/maplex/slimeEasy/regression/NativeRuntimeRegression.java': (None, '7d2d11593db22eca6ace544fb78d6647f541f74e'),
    'src/cargoRegression/java/top/maplex/slimeEasy/regression/CargoRegression.java': ('823f88861aaa29846e95cdf02edda127ed5ce2c7', 'd84caa30feba5e3bfebeeef11acf332abc6265b3'),
}
original = {}
for name, (old, new) in expected.items():
    path = base / name
    if old is None:
        assert not path.exists(), name
    else:
        assert blob(path.read_bytes()) == old, name
        original[name] = path.read_text()
outputs = dict(original)
name = 'build.gradle.kts'
text = outputs[name]
text = text.replace('// Normal builds remain pinned to the 26.2 production baseline.\n// Compatibility CI\n', '// Normal builds remain pinned to the 26.2 production baseline.\n// Compatibility CI\n') if False else text
old = '// Normal builds remain pinned to the 26.2 production baseline. Compatibility CI\n// may override only the Paper dev bundle so CraftBukkit/NMS and Paper API come\n// from the same candidate build while checking 26.3.'
new = '// Production is compiled against the oldest supported native/API surface.\n// Compatibility builds may override only the dev bundle; the shipped bytecode\n// stays Java 21 and newer runtime checks use this same baseline-built JAR.'
assert text.count(old) == 1
text = text.replace(old, new)
old = 'compileOnly("com.github.decentsoftware-eu:decentholograms:2.10.1")'
assert text.count(old) == 1
text = text.replace(old, 'compileOnly("com.github.decentsoftware-eu.decentholograms:plugin:2.10.1") {\n        isTransitive = false\n    }')
text = text.replace('    jvmToolchain(25)', '    jvmToolchain(25)\n    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)')
text += '\n// A Java 25 compiler must still emit classes loadable by the Java 21 floor.\ntasks.withType<JavaCompile>().configureEach {\n    options.release.set(21)\n}\n'
outputs[name] = text
name = 'gradle/libs.versions.toml'
outputs[name] = outputs[name].replace('paper = "26.3-rc-3.build.1-alpha"', 'paper = "1.21.11-R0.1-SNAPSHOT"').replace('minecraft = "26.2"', 'minecraft = "1.21.11"')
for name in ('src/main/resources/paper-plugin.yml', 'src/cargoRegression/resources/paper-plugin.yml'):
    assert outputs[name].count("api-version: '26.2'") == 1
    outputs[name] = outputs[name].replace("api-version: '26.2'", "api-version: '1.21.11'")
name = 'src/main/kotlin/top/maplex/slimeEasy/machine/butcher/FakePlayerFactory.kt'
text = outputs[name]
marker = text.index('    private class FakeCraftPlayer(')
start = text.rfind('    /**', 0, marker)
assert start > 0
text = text[:start] + '}\n'
outputs[name] = text.replace('import org.bukkit.Particle\n', '').replace('FakeCraftPlayer', 'FakeCraftPlayerBridge')
name = 'src/cargoRegression/java/top/maplex/slimeEasy/regression/CargoRegression.java'
assert outputs[name].count('                verifyPort(custom);') == 1
outputs[name] = outputs[name].replace('                verifyPort(custom);', '                verifyPort(custom);\n                NativeRuntimeRegression.verify(this);')
for name, relative in [('src/main/java/top/maplex/slimeEasy/machine/butcher/FakeCraftPlayerBridge.java', 'FakeCraftPlayerBridge.java'), ('src/cargoRegression/java/top/maplex/slimeEasy/regression/NativeRuntimeRegression.java', 'NativeRuntimeRegression.java')]:
    outputs[name] = (inputs / relative).read_text()
for name, text in outputs.items():
    assert blob(text.encode()) == expected[name][1], (name, blob(text.encode()))
for name, text in outputs.items():
    target = base / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text)
Path('audit-evidence').mkdir(exist_ok=True)
Path('audit-evidence/source-manifest.json').write_text(json.dumps({name: pair[1] for name, pair in expected.items()}, indent=2))
print('Applied eight exact reviewed floor/native-test files; no source recipe or item mapping changes.')
