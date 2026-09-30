from pathlib import Path
import hashlib

path = Path('src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java')
def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
assert blob(path.read_bytes()) == 'd1a743aec75d1777bfc4999bbefbb7777baa24e6'
text = path.read_text()
replacements = (
    ('        ItemMeta canonicalMeta = sfItem.getItem().getItemMeta();', '''        // Dynamic presentation hooks belong to addons. Never run those hooks on
        // the live player item, including during a read-only scan.
        ItemStack presentationItem = item.clone();
        if (presentationItem == item) {
            throw new IllegalStateException("Item clone did not provide an isolated presentation snapshot");
        }
        ItemMeta canonicalMeta = sfItem.getItem().getItemMeta();'''),
    ('state = DynamicState.capture(item, sfItem);', 'state = DynamicState.capture(presentationItem, sfItem);'),
    ('''            item.setItemMeta(currentMeta);

            if (hasCjkLore && stateCaptured && state.safelyRestorable) {
                restoreDynamicPresentation(item, sfItem, state);
            }

            ItemMeta finalMeta = item.getItemMeta();''', '''            presentationItem.setItemMeta(currentMeta);

            if (hasCjkLore && stateCaptured && state.safelyRestorable) {
                restoreDynamicPresentation(presentationItem, sfItem, state);
            }

            if (presentationItem.getType() != item.getType()
                    || presentationItem.getAmount() != item.getAmount()
                    || !itemId.equals(Slimefun.getItemDataService().getItemData(presentationItem).orElse(null))) {
                throw new IllegalStateException("Presentation hook changed item identity, material or amount");
            }
            ItemMeta presentationMeta = presentationItem.getItemMeta();
            ItemMeta finalMeta = originalMeta.clone();
            finalMeta.displayName(presentationMeta.displayName());
            finalMeta.lore(presentationMeta.lore());
            // Retain the existing legacy-lore recovery behavior for missing
            // dynamic markers, but never overwrite a pre-existing typed value.
            if (stateCaptured && state.safelyRestorable && state.charge != null) {
                presentationMeta.getPersistentDataContainer().set(
                        Slimefun.getRegistry().getItemChargeDataKey(), PersistentDataType.FLOAT, state.charge);
            }
            presentationMeta.getPersistentDataContainer().copyTo(finalMeta.getPersistentDataContainer(), false);
            if (!item.setItemMeta(finalMeta)) {
                throw new IllegalStateException("Item rejected its repaired presentation metadata");
            }'''),
)
for before, after in replacements:
    assert text.count(before) == 1, before
    text = text.replace(before, after)
path.write_text(text)
assert blob(path.read_bytes()) == '592b339085d444465d6bb0fbf440f8a865068e9d'
print('REVIEWED_PRESENTATION_SOURCE', blob(path.read_bytes()))
