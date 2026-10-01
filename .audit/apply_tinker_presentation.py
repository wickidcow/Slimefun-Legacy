from pathlib import Path
import base64
import hashlib
import json
import subprocess

root = Path('addon')
sha = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()

def read(name, expected):
    data = (root/name).read_bytes()
    assert sha(data) == expected, (name, sha(data))
    return data.decode()

def replace(text, before, after, count):
    assert text.count(before) == count, (before, text.count(before))
    return text.replace(before, after)

item = 'src/main/java/io/github/sefiraat/slimetinker/utils/ItemUtils.java'
s = read(item, '707cd6e386bdbfdbf283936674deb755906b3e84')
s = replace(s, 'im.setLore(lore);', 'im.lore(ItemPresentation.generatedLore(lore));', 2)
s = replace(s, 'im.setDisplayName(name);', 'im.displayName(ItemPresentation.generatedName(name));', 1)
(root/item).write_text(s)
theme = 'src/main/java/io/github/sefiraat/slimetinker/utils/ThemeUtils.java'
s = read(theme, 'c14bd397c8487efed11e5ce4029475ca1a07ae56')
s = replace(s, 'import java.util.ArrayList;\n', 'import java.util.Arrays;\n', 1)
s = replace(s, '''    @Nonnull
    private static List<String> mutableLore(@Nonnull ItemMeta meta) {
        List<String> lore = meta.getLore();
        return lore != null ? lore : new ArrayList<>();
    }

''', '', 1)
old = '''        List<String> lore = mutableLore(im);
        for (String s : loreLines) {
            lore.add(ThemeUtils.PASSIVE + s);
        }
        lore.add("");
        lore.add(itemTypeDescriptor(type));
        im.setLore(lore);'''
assert s.count(old) == 4
# The first two overloads receive arrays; the next two receive Lists.
for lines in ('Arrays.asList(loreLines)', 'Arrays.asList(loreLines)', 'loreLines', 'loreLines'):
    s = s.replace(old, '        im.lore(ItemPresentation.themedLore(im.lore(), ' + lines
            + ', ThemeUtils.PASSIVE.toString(), itemTypeDescriptor(type)));', 1)
(root/theme).write_text(s)
pom = 'pom.xml'
s = read(pom, 'ced58e062ac85137eee0b0826f52b5440fc8740e')
anchor = '    <dependencies>\n'
s = replace(s, anchor, anchor + '        <dependency><groupId>junit</groupId><artifactId>junit</artifactId><version>4.13.2</version><scope>test</scope></dependency>\n', 1)
anchor = '        <defaultGoal>clean package</defaultGoal>'
s = replace(s, anchor, '        <defaultGoal>clean verify</defaultGoal>', 1)
anchor = '        </plugins>\n        <defaultGoal>'
s = replace(s, anchor, '''            <plugin>
                <groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId><version>3.5.2</version>
                <configuration><failIfNoTests>true</failIfNoTests></configuration>
            </plugin>
        </plugins>
        <defaultGoal>''', 1)
(root/pom).write_text(s)
workflow = '.github/workflows/build.yml'
s = read(workflow, '871971b575f251b8dd9ef0cd9b1757000dd4a124')
s = replace(s, 'run: mvn clean package -DskipTests', 'run: mvn --batch-mode clean verify', 1)
(root/workflow).write_text(s)
files = {
    'src/main/java/io/github/sefiraat/slimetinker/utils/ItemPresentation.java': '2578390b9361c91113dfac79b0ad773b19753598',
    'src/test/java/io/github/sefiraat/slimetinker/utils/ItemPresentationTest.java': '41e0a3b2c6f63988152e6d87939499962f48430b',
}
for name, expected in files.items():
    assert not (root/name).exists(), name
    result = subprocess.run(['gh', 'api', f'repos/wickidcow/SF_SlimeTinkerIE2/git/blobs/{expected}'],
                            check=True, capture_output=True, text=True)
    response = json.loads(result.stdout)
    assert response['encoding'] == 'base64'
    data = base64.b64decode(response['content'])
    assert sha(data) == expected, name
    (root/name).parent.mkdir(parents=True, exist_ok=True)
    (root/name).write_bytes(data)
paths = [item, theme, pom, workflow, *files]
# Save the exact proposed source before compatibility tooling rewrites dependency coordinates.
manifest = {}
for name in paths:
    data = (root/name).read_bytes()
    destination = Path('audit-evidence/proposed-source')/name
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)
    manifest[name] = sha(data)
    print('PROPOSED_TINKER_SOURCE', manifest[name], name)
Path('audit-evidence/proposed-blobs.json').write_text(json.dumps(manifest, indent=2))
subprocess.run(['git', '-C', str(root), 'diff', '--check'], check=True)
