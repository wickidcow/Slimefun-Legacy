from pathlib import Path
import hashlib
import json


def replace(path, before, after):
    text = path.read_text()
    assert text.count(before) == 1, (path, before)
    path.write_text(text.replace(before, after))


pin = Path('compatibility/worldedit-runtime.json')
modern = json.loads(pin.read_text())
assert modern['modrinth_version_id'] == 'J1eeOh6C'
modern['minecraft_versions'] = ['26.2', '26.3']
modern['java_major'] = 25
floor = {
    'schema': 1, 'plugin': 'WorldEdit', 'version': '7.3.19', 'release_channel': 'release',
    'minecraft_versions': ['1.21.11'], 'java_major': 21, 'modrinth_version_id': '2YDdVDmG',
    'filename': 'worldedit-bukkit-7.3.19.jar',
    'url': 'https://cdn.modrinth.com/data/1u6JkXh5/versions/2YDdVDmG/worldedit-bukkit-7.3.19.jar',
    'sha512': '105b1f2c3d1f55ba8db3357421655f0d2597f736b1419336aae5c9210a492c0a8d6c136dfc1143131bfc4186f247393ef0b1eabf497796030317371e5196acde',
}
pin.write_text(json.dumps({'schema': 2, 'plugin': 'WorldEdit', 'variants': [floor, modern]}, indent=2) + '\n')
helper = Path('scripts/prepare_worldedit_runtime.py')
replace(helper, 'def validate_pin(', '''def select_pin(contract: dict, minecraft: str) -> dict:
    if contract.get("schema") != 2 or contract.get("plugin") != "WorldEdit":
        raise ValueError("Unsupported multi-runtime WorldEdit contract")
    matches = [pin for pin in contract.get("variants", []) if minecraft in pin.get("minecraft_versions", [])]
    if len(matches) != 1:
        raise ValueError("Expected exactly one dependency for this Minecraft version")
    validate_pin(matches[0], minecraft)
    return dict(matches[0])


def validate_pin(''')
replace(helper, '    url = urllib.parse.urlsplit(pin.get("url", ""))', '''    if pin.get("java_major") != (21 if minecraft == "1.21.11" else 25):
        raise ValueError("Dependency must preserve the reviewed runtime Java floor")
    url = urllib.parse.urlsplit(pin.get("url", ""))''')
replace(helper, '    plugins.mkdir(parents=True, exist_ok=True)', '''        plugin_class = archive.read("com/sk89q/worldedit/bukkit/WorldEditPlugin.class")
        if len(plugin_class) < 8 or plugin_class[:4] != bytes.fromhex("cafebabe"):
            raise ValueError("WorldEdit plugin entry point is not a Java class")
        if int.from_bytes(plugin_class[6:8], "big") > pin["java_major"] + 44:
            raise ValueError("WorldEdit entry point exceeds the selected Java runtime")
    plugins.mkdir(parents=True, exist_ok=True)''')
replace(helper, '"minecraft": minecraft, "url": pin["url"]}', '"minecraft": minecraft, "java_major": pin["java_major"], "url": pin["url"]}')
replace(helper, 'result = stage(json.loads(args.pin.read_text()), args.minecraft, args.plugins)', 'result = stage(select_pin(json.loads(args.pin.read_text()), args.minecraft), args.minecraft, args.plugins)')
test = Path('scripts/test_prepare_worldedit_runtime.py')
replace(test, 'from prepare_worldedit_runtime import stage, validate_pin', 'from prepare_worldedit_runtime import stage, validate_pin, select_pin')
replace(test, "z.writestr('plugin.yml', 'name: WorldEdit\\nversion: fixture\\n')", "z.writestr('plugin.yml', 'name: WorldEdit\\nversion: fixture\\n')\n            z.writestr('com/sk89q/worldedit/bukkit/WorldEditPlugin.class', bytes.fromhex('cafebabe00000041'))")
replace(test, "'version': 'fixture', 'release_channel': 'beta',", "'version': 'fixture', 'release_channel': 'beta', 'java_major': 25,")
text = test.read_text()
assert text.count("stage(self.pin, '1.21.11'") == 2
test.write_text(text.replace("stage(self.pin, '1.21.11'", "stage(dict(self.pin, java_major=21), '1.21.11'"))
replace(test, "for version in ('1.21.11', '26.2', '26.3'): validate_pin(pin, version)", "for version in ('1.21.11', '26.2', '26.3'): validate_pin(select_pin(pin, version), version)\n        self.assertEqual(21, select_pin(pin, '1.21.11')['java_major'])\n        self.assertEqual(25, select_pin(pin, '26.3')['java_major'])")
replace(test, "\n\nif __name__", '''
    def test_ambiguous_or_missing_runtime_is_refused(self):
        for variants in ([], [self.pin, self.pin]):
            with self.assertRaises(ValueError):
                select_pin({'schema': 2, 'plugin': 'WorldEdit', 'variants': variants}, '26.3')

    def test_newer_bytecode_cannot_enter_java21_plugins(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as z:
            z.writestr('plugin.yml', 'name: WorldEdit\\n')
            z.writestr('com/sk89q/worldedit/bukkit/WorldEditPlugin.class', bytes.fromhex('cafebabe00000045'))
        payload = data.getvalue()
        pin = dict(self.pin, java_major=21, sha512=hashlib.sha512(payload).hexdigest())
        with self.assertRaises(ValueError): stage(pin, '1.21.11', self.plugins, lambda _: payload)
        self.assertFalse(self.plugins.exists())

    def test_raising_the_floor_runtime_is_refused(self):
        with self.assertRaises(ValueError): validate_pin(self.pin, '1.21.11')

    def test_selected_variant_is_a_detached_dictionary(self):
        selected = select_pin({'schema': 2, 'plugin': 'WorldEdit', 'variants': [self.pin]}, '26.3')
        selected['filename'] = 'changed.jar'
        self.assertEqual('worldedit-fixture.jar', self.pin['filename'])


if __name__''')
smoke = Path('scripts/full_stack_runtime_smoke.sh')
text = smoke.read_text()
assert text.count('AbstractMethodError|IncompatibleClassChangeError') == 2
smoke.write_text(text.replace('AbstractMethodError|IncompatibleClassChangeError', 'AbstractMethodError|IncompatibleClassChangeError|UnsupportedClassVersionError|InvalidPluginException'))
for path in (pin, helper, test, smoke):
    data = path.read_bytes()
    sha = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    print('CORRECTED_RUNTIME_SOURCE', sha, path)
