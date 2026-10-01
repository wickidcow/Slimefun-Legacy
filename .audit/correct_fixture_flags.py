import hashlib
from pathlib import Path

path = Path('scripts/upgrade-fixture/LegacyItemUpgradeProbe.java')
def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert blob(path.read_bytes()) == '577ce483cf768e92ad232311b0490232a395cc63'
s = path.read_text()
s = s.replace('import org.bukkit.util.io.BukkitObjectOutputStream;', 'import org.bukkit.util.io.BukkitObjectInputStream;\nimport org.bukkit.util.io.BukkitObjectOutputStream;\nimport java.io.ByteArrayInputStream;')
s = s.replace('meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);', 'meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);')
s = s.replace('        var block = Bukkit.getWorlds().get(0).getBlockAt(8, 70, 8);', '''        // A flag with no matching component is not necessarily persisted by the
        // historical server itself. Keep an explicit source-version control,
        // rather than treating that old writer behavior as an upgrade defect.
        ItemStack control = new ItemStack(Material.COAL);
        ItemMeta controlMeta = control.getItemMeta();
        controlMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        control.setItemMeta(controlMeta);
        ItemStack persistedControl = ItemStack.deserializeBytes(control.serializeAsBytes());
        require(control.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES), "Source flag control was not set");
        require(!persistedControl.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES),
                "Recheck source flag behavior before changing the fixture contract");
        Files.writeString(directory.resolve("source-flag-control.json"), JSON.toJson(Map.of(
                "source_minecraft", SOURCE, "ephemeral", snapshot(control), "persisted", snapshot(persistedControl))));
        var block = Bukkit.getWorlds().get(0).getBlockAt(8, 70, 8);''', 1)
s = s.replace('            byte[] raw = item.serializeAsBytes();', '''            byte[] raw = item.serializeAsBytes();
            require(JSON.toJson(snapshot(item)).equals(JSON.toJson(snapshot(ItemStack.deserializeBytes(raw)))),
                    "Historical native round trip failed before any upgrade at " + index);''')
s = s.replace('                Files.write(directory.resolve(index + ".legacy"), Base64.getEncoder().encode(bytes.toByteArray()));', '''                byte[] serialized = bytes.toByteArray();
                try (var input = new BukkitObjectInputStream(new ByteArrayInputStream(serialized))) {
                    ItemStack restored = (ItemStack) input.readObject();
                    require(JSON.toJson(snapshot(item)).equals(JSON.toJson(snapshot(restored))),
                            "Historical object-stream round trip failed before any upgrade at " + index);
                }
                Files.write(directory.resolve(index + ".legacy"), Base64.getEncoder().encode(serialized));''')
assert blob(s.encode()) == '4301f5d6ce2d24d098c95fff09ef7b00701b05b4'
path.write_text(s)
print('SOURCE_FLAG_CONTROL_AND_PERSISTED_FIXTURE', blob(path.read_bytes()))
