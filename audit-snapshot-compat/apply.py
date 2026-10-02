from pathlib import Path
import hashlib

root = Path('src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util')
blob = lambda b: hashlib.sha1(b'blob ' + str(len(b)).encode() + b'\0' + b).hexdigest()
p = root / 'InvSnapshot.java'
assert blob(p.read_bytes()) == '1ad45948227fcb39503ff3a69ab524272f593259'
s = p.read_text().replace('this.snapshot = copySnapshot(snapshot);', 'this.snapshot = List.copyOf(copySnapshot(snapshot));')
s = s.replace('Returns detached pairs and items, never the baseline used by inventory save comparisons.',
              'Returns an editable detached list of pairs and items, never the saved comparison baseline.')
s = s.replace('return List.copyOf(copy);', 'return copy;')
p.write_text(s)
p = root / 'InvStorageUtils.java'
assert blob(p.read_bytes()) == '4705352c238a9c117b35314efc20965fa3e4a889'
a = '''        // Use the owned baseline directly; the public snapshot getter deliberately makes a deep copy.
        return snapshot == null
                ? getChangedSlots((List<Pair<ItemStack, Integer>>) null, currContent)
                : snapshot.getChangedSlots(currContent);'''
b = '''        if (snapshot != null && snapshot.getClass() == InvSnapshot.class) {
            // Avoid copying the core's owned baseline for each ordinary save comparison.
            return snapshot.getChangedSlots(currContent);
        }
        // Retain historical getter dispatch for addon-defined snapshot subclasses.
        return getChangedSlots(snapshot == null ? null : snapshot.getSnapshot(), currContent);'''
s = p.read_text(); assert s.count(a) == 1
p.write_text(s.replace(a, b))
p = Path('src/test/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/InvSnapshotOwnershipTest.java')
assert blob(p.read_bytes()) == '79be556ab35d65c52a72214c17dba9f4518fcdc9'
needle = '    private static class CountingItem extends ItemStack {'
extra = '''    @Test
    void exportedListCanStillBeEditedWithoutChangingTheBaseline() {
        var snapshot = new InvSnapshot(new ItemStack[] {richItem(37), null});
        var exported = snapshot.getSnapshot();
        exported.clear();
        exported.add(new Pair<>(richItem(12), 12));
        assertTrue(snapshot.getChangedSlots(new ItemStack[] {richItem(37), null}).isEmpty());
        assertEquals(2, snapshot.getSnapshot().size());
    }

    @Test
    void staticComparatorRetainsCustomSubclassGetterDispatch() {
        var snapshot = new InvSnapshot(new ItemStack[] {richItem(37)}) {
            @Override
            public List<Pair<ItemStack, Integer>> getSnapshot() {
                return List.of(new Pair<>(richItem(12), 12));
            }
        };
        var current = new ItemStack[] {richItem(12)};
        assertTrue(InvStorageUtils.getChangedSlots(snapshot, current).isEmpty());
        // The historical instance method compares its own baseline, not the overridden export.
        assertEquals(Set.of(0), snapshot.getChangedSlots(current));
    }

    @Test
    void staticComparatorDoesNotNewlyDispatchSubclassComparisonOverrides() {
        var snapshot = new InvSnapshot(new ItemStack[] {richItem(37)}) {
            @Override
            public Set<Integer> getChangedSlots(ItemStack[] current) {
                throw new AssertionError("The historical static comparator must not call this override");
            }
        };
        assertTrue(InvStorageUtils.getChangedSlots(snapshot, new ItemStack[] {richItem(37)}).isEmpty());
        assertEquals(Set.of(0), InvStorageUtils.getChangedSlots(snapshot, new ItemStack[] {richItem(12)}));
    }

'''
s = p.read_text(); assert s.count(needle) == 1
p.write_text(s.replace(needle, extra + needle))
