from pathlib import Path
import hashlib
import subprocess

# checkout is shallow; explicitly fetch the pinned original before git-show comparisons.
subprocess.run(['git', 'fetch', '--depth', '1', 'origin', 'a194ab1d0d5dd1cfc72caada225961ba0852ab87'], check=True)
root = Path('src/test/java/com/xzavier0722/mc/plugin/slimefun4/storage')
path = root / 'controller/InventorySerializationFailureTest.java'
data = path.read_bytes()
assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == 'b84c00c6d6537a053cd216b072b40ee57009acc6'
addition = '''    @ParameterizedTest
    @EnumSource(Kind.class)
    void exportedBaselineCannotHideAnUnpersistedQuantityChange(Kind kind) throws Exception {
        try (var harness = new Harness(kind)) {
            harness.set(0, oldItem(37));
            harness.saveAndComplete();
            var snapshot = (com.xzavier0722.mc.plugin.slimefun4.storage.util.InvSnapshot)
                    harness.acknowledgedSnapshot();
            var exported = snapshot.getSnapshot().getFirst();
            exported.getFirstValue().setAmount(12);
            exported.setSecondValue(12);
            harness.set(0, oldItem(12));
            int submitted = harness.store.submitted;
            harness.saveAndComplete();
            assertTrue(harness.store.submitted > submitted, "Changed quantity must actually be submitted");
            harness.store.reopen();
            assertEquals(oldItem(12), harness.store.item(0));
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void exportedBaselineCannotHideAnUnpersistedMetadataChange(Kind kind) throws Exception {
        try (var harness = new Harness(kind)) {
            harness.set(0, oldItem(37));
            harness.saveAndComplete();
            ItemStack changed = oldItem(37);
            var meta = changed.getItemMeta();
            meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, "UNREGISTERED_OLD_ID");
            changed.setItemMeta(meta);
            var snapshot = (com.xzavier0722.mc.plugin.slimefun4.storage.util.InvSnapshot)
                    harness.acknowledgedSnapshot();
            snapshot.getSnapshot().getFirst().setFirstValue(changed.clone());
            harness.set(0, changed);
            int submitted = harness.store.submitted;
            harness.saveAndComplete();
            assertTrue(harness.store.submitted > submitted, "Changed metadata must actually be submitted");
            harness.store.reopen();
            assertEquals(changed, harness.store.item(0));
        }
    }

'''
needle = '    private static ItemStack oldItem(int amount) {'
text = data.decode()
assert text.count(needle) == 1
path.write_text(text.replace(needle, addition + needle))
path = root / 'util/InvSnapshotOwnershipTest.java'
text = path.read_text()
assert text.count('assertNull(second.getFirstValue());') == 1
text = text.replace('assertNull(second.getFirstValue());', '''assertNull(second.getFirst().getFirstValue());
            assertEquals(Set.of(1), InvStorageUtils.getChangedSlots(
                    List.of(new Pair<ItemStack, Integer>(null, 0)),
                    new ItemStack[] {null, new ItemStack(Material.DIAMOND)}));''')
path.write_text(text)
