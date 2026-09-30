from pathlib import Path
import hashlib

root = Path('.')
paths = {
    'utils': 'src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/DataUtils.java',
    'record': 'src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/common/RecordSet.java',
    'maintenance': 'src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/PersistedItemStorageMaintenance.java',
    'block': 'src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataController.java',
    'profile': 'src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/ProfileDataController.java',
}
expected = {
    'utils': ('42e7d127b6a923f04761244dafc619a239ebf34f', 'ce3b76f032f6e75eb2d5aa098bbc7bb5ea73e9af'),
    'record': ('5f800e4f011a89f8262905e1d813c402923cee2c', 'af949d5a021b33a224732d25bff186e3d4122f96'),
    'maintenance': ('f4f24ed1587cd60f9e05f8c2da3b1d0be313c838', '2eaf750ac1e780d5564abad99e31c2a0e31a28f4'),
    'block': ('ba4d0cb661405670c377fbebb279fdb073f70f07', '8f1f84dadc5081d5c9256528211e282b5615d487'),
    'profile': ('1cc398e40adcb628dae9afee6af8020bfaf0b858', 'cb885c58368684907e1b149ae9cfc9501250540b'),
}

def sha(text):
    data = text.encode('utf-8')
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

original = {k: (root / p).read_text() for k, p in paths.items()}
for key, text in original.items():
    assert sha(text) == expected[key][0], ('Unexpected baseline', paths[key], sha(text))
modified = original.copy()
u = original['utils']
start = u.index('    public static byte[] serializeItemStackBytes(ItemStack itemStack) {')
catch = u.index('        } catch (IllegalArgumentException e) {', start)
modified['utils'] = u[:start] + '''    public static byte[] serializeItemStackBytes(ItemStack itemStack) {
        try {
            return serializeItemStackBytesForStorage(itemStack);
''' + u[catch:]
strict = '''    /**
     * Encodes an inventory value without converting a failed non-empty item into an empty slot.
     *
     * <p>Persistent writers must use this method and finish staging the complete inventory before
     * submitting writes. The historical tolerant serializer remains available to addons. Both
     * methods use the same native codec, identifiers and size policy. Only genuinely empty inputs
     * return an empty payload here; even Paper's empty-item exception is propagated if it occurs
     * after a non-empty snapshot was observed, so the caller can retry rather than erase saved data.
     *
     * @param itemStack item snapshot to serialize, or null for an empty slot
     * @return existing versioned binary representation, or empty bytes for an empty input
     * @throws RuntimeException if serialization or the existing storage size check fails
     */
    public static byte[] serializeItemStackBytesForStorage(@Nullable ItemStack itemStack) {
        if (isEmptyItemStack(itemStack)) {
            return new byte[0];
        }

        Debug.log(TestCase.BACKPACK, "Serializing itemstack: " + itemStack);
        var itemData = ItemStackDataCodec.serialize(itemStack);

        // The configured MySQL limit cannot apply to a smaller payload. Avoid consulting
        // global configuration on the overwhelmingly common normal-sized item path.
        if (itemData.length > MYSQL_MEDIUMBLOB_MAX_BYTES
                && !Slimefun.getConfigManager().isBypassItemLengthCheck()
                && Slimefun.getDatabaseManager().getBlockDataStorageType() == StorageType.MYSQL) {
            throw new IllegalArgumentException(
                    "Detected an oversized item. Please contact the plugin developer responsible for that item: "
                            + StringUtil.itemStackToString(itemStack)
                            + ", size = "
                            + itemData.length);
        }

        return itemData;
    }

'''
modified['utils'] = modified['utils'].replace('    static boolean isEmptyItemStack(', strict + '    static boolean isEmptyItemStack(', 1)
modified['utils'] = modified['utils'].replace('An error occurred while serializing an item; an empty value will be stored.', 'An error occurred while serializing an item; no encoded item data was produced.')
modified['record'] = original['record'].replace('        putValue(key, DataUtils.serializeItemStackBytes(itemStack));', '''        // Reject writes before invoking an addon-supplied ItemStack serializer.
        checkReadonly();
        putValue(key, DataUtils.serializeItemStackBytesForStorage(itemStack));''', 1)
modified['maintenance'] = original['maintenance'].replace('DataUtils.serializeItemStackBytes(item)', 'DataUtils.serializeItemStackBytesForStorage(item)')
b = original['block']
for method, arg in [('saveBlockInventoryAsync', '@Nonnull SlimefunBlockData blockData'), ('saveUniversalInventoryAsync', '@Nonnull SlimefunUniversalData universalData')]:
    marker = f'    public CompletableFuture<Void> {method}({arg}) {{\n'
    pos = b.index(marker) + len(marker)
    end = b.index('\n    }', pos)
    body = b[pos:end]
    new = '''        try {
''' + ''.join('    ' + line + '\n' if line else '\n' for line in body.splitlines()).rstrip('\n') + '''
        } catch (RuntimeException | LinkageError failure) {
            // No write has been submitted and no dirty token/snapshot was acknowledged.
            // Keep the previous persisted baseline and let a later save retry this state.
            return CompletableFuture.failedFuture(failure);
        }'''
    b = b[:pos] + new + b[end:]
start = b.index('    private Map<Integer, InventoryWrite> stageInventoryWrites(')
end = b.index('    @Nullable private ItemStack[] copyInventoryContents', start)
section = b[start:end]
assert section.count('if (item == null)') == 1
b = b[:start] + section.replace('if (item == null)', 'if (item == null || item.isEmpty())') + b[end:]
for begin, endmark, ident in [
    ('    private void scheduleBlockInvUpdate(', '    /**\n     * Save universal inventory', 'lKey'),
    ('    private void scheduleUniversalInvUpdate(', '    @Override\n    public void shutdown()', 'uuid'),
]:
    start = b.index(begin)
    end = b.index(endmark, start)
    section = b[start:end]
    old = '''            } catch (IllegalArgumentException e) {
                Slimefun.logger().log(Level.WARNING, e.getMessage());
'''
    assert section.count(old) == 1
    section = section.replace(old, '''            } catch (RuntimeException | LinkageError failure) {
                logger.log(Level.WARNING,
                        "Could not serialize inventory slot " + ''' + ident + ''' + ":" + slot
                                + "; the existing stored value was retained.", failure);
''')
    b = b[:start] + section + b[end:]
modified['block'] = b
p = original['profile']
start = p.index('    private Map<Integer, BackpackWrite> stageBackpackWrites(')
end = p.index('    private ItemStack[] copyBackpackContents', start)
section = p[start:end]
assert section.count('if (item == null)') == 1
p = p[:start] + section.replace('if (item == null)', 'if (item == null || item.isEmpty())') + p[end:]
p = p.replace('''            Slimefun.logger()
                    .log(Level.WARNING, "Could not stage backpack " + backpackId + " for persistence", failure);''', '''            logger.log(Level.WARNING, "Could not stage backpack " + backpackId + " for persistence", failure);''')
modified['profile'] = p
for key, text in modified.items():
    assert sha(text) == expected[key][1], ('Unexpected result', paths[key], sha(text))
for key, text in modified.items():
    (root / paths[key]).write_text(text)
    print('APPLIED', sha(text), paths[key])
