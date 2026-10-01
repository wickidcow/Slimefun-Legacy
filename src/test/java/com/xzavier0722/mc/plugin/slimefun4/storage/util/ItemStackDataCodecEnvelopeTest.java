package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Format regressions for allocation-only read changes; these are generated fixtures, not captured worlds. */
class ItemStackDataCodecEnvelopeTest {
    private static final byte[] HEADER = {'S', 'F', '2', 0};
    private static final NamespacedKey ITEM_ID = key("slimefun:slimefun_item");
    private static final NamespacedKey OWNER_ID = key("slimefun:owner_uuid");
    private static final NamespacedKey CHARGE = key("slimefun:item_charge");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void recognizesOnlyTheExactFourBytePrefixWithoutChangingInput() {
        assertFalse(ItemStackDataCodec.isCurrent(null));
        assertFalse(ItemStackDataCodec.isLegacy(null));
        assertFalse(ItemStackDataCodec.isLegacy(new byte[0]));
        for (int length = 0; length < HEADER.length; length++) {
            assertFalse(ItemStackDataCodec.isCurrent(Arrays.copyOf(HEADER, length)));
        }
        // A header alone is still classified as native; payload validation belongs to the reader.
        assertTrue(ItemStackDataCodec.isCurrent(HEADER.clone()));
        assertFalse(ItemStackDataCodec.isCurrent(new byte[] {0, 'S', 'F', '2', 0}));
        for (int position = 0; position < HEADER.length; position++) {
            for (int value = 0; value < 256; value++) {
                byte[] stored = Arrays.copyOf(HEADER, HEADER.length + 17);
                stored[position] = (byte) value;
                byte[] original = stored.clone();
                assertEquals((byte) value == HEADER[position], ItemStackDataCodec.isCurrent(stored));
                assertArrayEquals(original, stored);
            }
        }
    }

    @Test
    void classificationMatchesTheHistoricalImplementationAcrossPayloadLengths() {
        var random = new Random(0x534632L);
        for (int length : new int[] {0, 1, 2, 3, 4, 5, 31, 64, 1024, 65536}) {
            for (int sample = 0; sample < 32; sample++) {
                byte[] stored = new byte[length];
                random.nextBytes(stored);
                if (length >= HEADER.length && sample % 2 == 0) {
                    System.arraycopy(HEADER, 0, stored, 0, HEADER.length);
                }
                boolean historical = stored.length >= HEADER.length
                        && Arrays.equals(HEADER, Arrays.copyOf(stored, HEADER.length));
                assertEquals(historical, ItemStackDataCodec.isCurrent(stored));
                assertEquals(stored.length > 0 && !historical, ItemStackDataCodec.isLegacy(stored));
            }
        }
    }

    @Test
    void mimeByteDecoderPreservesHistoricalAsciiToleranceForEveryByteValue() {
        byte[] text = "U0YyAAECAwQF".getBytes(StandardCharsets.US_ASCII);
        for (int position = 0; position <= text.length; position++) {
            for (int value = 0; value < 256; value++) {
                byte[] candidate = insert(text, position, new byte[] {(byte) value});
                byte[] historical;
                try {
                    historical = Base64.getMimeDecoder().decode(new String(candidate, StandardCharsets.US_ASCII));
                } catch (IllegalArgumentException expected) {
                    assertThrows(IllegalArgumentException.class, () -> Base64.getMimeDecoder().decode(candidate));
                    continue;
                }
                assertArrayEquals(historical, Base64.getMimeDecoder().decode(candidate));
            }
        }
    }

    @Test
    void paddedUnpaddedAndLineWrappedRecordsPreserveIdentityAmountAndExactCharge() throws Exception {
        var expected = existingItem();
        for (byte[] raw : List.of(ItemStackDataCodec.serialize(expected), historicalObjectBytes(expected))) {
            for (byte[] stored : List.of(
                    Base64.getEncoder().encode(raw),
                    Base64.getEncoder().withoutPadding().encode(raw),
                    Base64.getMimeEncoder(32, new byte[] {'\r', '\n'}).encode(raw))) {
                byte[] original = stored.clone();
                assertPreserved(expected, ItemStackDataCodec.deserialize(stored));
                assertArrayEquals(original, stored);
            }
        }
    }

    @Test
    void ignoredMimeBytesRemainReadableThroughBothRealCodecPaths() throws Exception {
        var expected = existingItem();
        byte[] ignored = new byte[132];
        ignored[0] = ' ';
        ignored[1] = '\t';
        ignored[2] = '\r';
        ignored[3] = '\n';
        for (int index = 4; index < ignored.length; index++) {
            ignored[index] = (byte) (128 + index - 4);
        }
        for (byte[] raw : List.of(ItemStackDataCodec.serialize(expected), historicalObjectBytes(expected))) {
            byte[] encoded = Base64.getEncoder().encode(raw);
            byte[] stored = insert(encoded, encoded.length / 2, ignored);
            byte[] original = stored.clone();
            assertPreserved(expected, ItemStackDataCodec.deserialize(stored));
            assertArrayEquals(original, stored);
        }
    }

    @Test
    void rejectsCorruptAndRecursiveEnvelopesWithoutChangingStoredBytesOrAliases() throws Exception {
        var expected = existingItem();
        var alias = ConfigurationSerialization.getClassByAlias("ItemMeta");
        assertNotNull(alias);
        for (byte[] stored : List.of(
                HEADER.clone(),
                Base64.getEncoder().encode(HEADER),
                Base64.getEncoder().encode(new byte[] {(byte) 0xac, (byte) 0xed, 0, 5}),
                Base64.getEncoder().encode(Base64.getEncoder().encode(ItemStackDataCodec.serialize(expected))),
                Base64.getEncoder().encode(Base64.getEncoder().encode(historicalObjectBytes(expected))))) {
            byte[] original = stored.clone();
            assertThrows(Exception.class, () -> ItemStackDataCodec.deserialize(stored));
            assertArrayEquals(original, stored);
            assertSame(alias, ConfigurationSerialization.getClassByAlias("ItemMeta"));
        }
        assertPreserved(
                expected, ItemStackDataCodec.deserialize(Base64.getEncoder().encode(historicalObjectBytes(expected))));
    }

    @Test
    void textEnvelopesRemainLegacyClassifiedUntilAnExplicitRewrite() throws Exception {
        var expected = existingItem();
        byte[] nativeBytes = ItemStackDataCodec.serialize(expected);
        byte[] text = Base64.getEncoder().encode(nativeBytes);
        byte[] original = text.clone();
        assertTrue(ItemStackDataCodec.isLegacy(text));
        assertFalse(ItemStackDataCodec.isCurrent(text));
        assertPreserved(expected, ItemStackDataCodec.deserialize(text));
        assertTrue(ItemStackDataCodec.isLegacy(text));
        assertArrayEquals(original, text);
        assertFalse(ItemStackDataCodec.isLegacy(nativeBytes));
    }

    @Test
    void repeatedReadsReturnIndependentItemsAndNeverReuseMutableState() throws Exception {
        var expected = existingItem();
        for (byte[] stored : List.of(
                ItemStackDataCodec.serialize(expected),
                Base64.getEncoder().encode(ItemStackDataCodec.serialize(expected)),
                Base64.getEncoder().encode(historicalObjectBytes(expected)))) {
            var first = ItemStackDataCodec.deserialize(stored);
            first.setAmount(1);
            var meta = first.getItemMeta();
            meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, "CHANGED");
            first.setItemMeta(meta);
            assertPreserved(expected, ItemStackDataCodec.deserialize(stored));
        }
    }

    private static ItemStack existingItem() {
        var item = new ItemStack(Material.IRON_INGOT, 37);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing addon item"));
        meta.lore(List.of(Component.text("Keep this owner-written lore")));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID, PersistentDataType.STRING, "LEGACY_ADDON_UNKNOWN_ID");
        pdc.set(OWNER_ID, PersistentDataType.STRING, "2f017f3a-8442-4ef2-9fba-456789abcdef");
        pdc.set(CHARGE, PersistentDataType.FLOAT, 123.4567F);
        item.setItemMeta(meta);
        return item;
    }

    private static void assertPreserved(ItemStack expected, ItemStack actual) {
        assertNotNull(actual);
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getAmount(), actual.getAmount());
        var expectedMeta = expected.getItemMeta();
        var actualMeta = actual.getItemMeta();
        assertEquals(expectedMeta.displayName(), actualMeta.displayName());
        assertEquals(expectedMeta.lore(), actualMeta.lore());
        var expectedData = expectedMeta.getPersistentDataContainer();
        var actualData = actualMeta.getPersistentDataContainer();
        assertEquals(expectedData.getKeys(), actualData.getKeys());
        assertEquals(
                expectedData.get(ITEM_ID, PersistentDataType.STRING), actualData.get(ITEM_ID, PersistentDataType.STRING));
        assertEquals(
                expectedData.get(OWNER_ID, PersistentDataType.STRING), actualData.get(OWNER_ID, PersistentDataType.STRING));
        assertTrue(actualData.has(CHARGE, PersistentDataType.FLOAT));
        assertEquals(
                Float.floatToRawIntBits(Objects.requireNonNull(expectedData.get(CHARGE, PersistentDataType.FLOAT))),
                Float.floatToRawIntBits(Objects.requireNonNull(actualData.get(CHARGE, PersistentDataType.FLOAT))));
    }

    private static byte[] insert(byte[] bytes, int position, byte[] extra) {
        byte[] result = new byte[bytes.length + extra.length];
        System.arraycopy(bytes, 0, result, 0, position);
        System.arraycopy(extra, 0, result, position, extra.length);
        System.arraycopy(bytes, position, result, position + extra.length, bytes.length - position);
        return result;
    }

    @SuppressWarnings("deprecation") // Reproduce historical object-stream records; never a production writer.
    private static byte[] historicalObjectBytes(ItemStack item) throws Exception {
        try (var bytes = new ByteArrayOutputStream();
                var output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            output.flush();
            return bytes.toByteArray();
        }
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString(value));
    }
}
