package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestDoctorHistoricalStackRepair {

    @TempDir
    Path folder;

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    private static ItemStack current() {
        ItemStack item = new ItemStack(Material.PAPER, 1);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true));
            meta.lore(List.of(Component.text("Radioactive", NamedTextColor.YELLOW)));
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "SMALL_URANIUM");
            meta.getPersistentDataContainer().set(new NamespacedKey("addon", "owner"), PersistentDataType.LONG, 12345L);
        });
        return item;
    }

    private static ItemStack historical(ItemStack template) {
        ItemStack old = new ItemStack(template);
        old.editMeta(meta -> {
            meta.displayName(Component.text("Small Uranium", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, false));
            meta.lore(List.of(
                    Component.text("Radioactive", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false)));
        });
        old.setAmount(37);
        return old;
    }

    @Test
    void createsOnlyNewDetachedPresentationAndPreservesAmountTypedPdc() {
        ItemStack template = current();
        ItemStack reference = new ItemStack(template);
        ItemStack old = historical(template);
        byte[] originalBytes = old.serializeAsBytes();
        byte[] referenceBytes = reference.serializeAsBytes();

        ItemStack repaired = DoctorHistoricalStackRepair.prepare(old, reference, template);

        assertNotNull(repaired);
        assertTrue(repaired.isSimilar(reference));
        assertEquals(37, repaired.getAmount());
        assertFalse(old.isSimilar(reference));
        assertArrayEquals(originalBytes, old.serializeAsBytes());
        assertArrayEquals(referenceBytes, reference.serializeAsBytes());
        assertEquals(
                12345L,
                repaired.getItemMeta()
                        .getPersistentDataContainer()
                        .get(new NamespacedKey("addon", "owner"), PersistentDataType.LONG));
        assertNotEquals(
                DoctorHistoricalStackRepair.fingerprint(old), DoctorHistoricalStackRepair.fingerprint(repaired));
    }

    @Test
    void doesNotRepairTypedPdcDifferencesOrDifferentLoreText() {
        ItemStack template = current();
        ItemStack reference = new ItemStack(template);
        ItemStack old = historical(template);
        old.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.INTEGER, 12345));
        assertNull(DoctorHistoricalStackRepair.prepare(old, reference, template));

        old = historical(template);
        old.editMeta(meta -> meta.lore(List.of(Component.text("Stored charge: 17"))));
        assertNull(DoctorHistoricalStackRepair.prepare(old, reference, template));
    }

    @Test
    void specializedTemplateCloneIsNeverUsedOrReliedUponForComparison() {
        ItemStack normal = current();
        ItemStack registered = new ItemStack(normal) {
            @Override
            public ItemStack clone() {
                throw new AssertionError("Doctor must not invoke registered item clone hooks");
            }
        };
        ItemStack old = historical(normal);
        ItemStack candidate = DoctorHistoricalStackRepair.prepare(old, normal, registered);
        assertNotNull(candidate);
        assertEquals(37, candidate.getAmount());
        assertTrue(candidate.isSimilar(normal));
    }

    @Test
    void presentationRepairRequiresExactNonPresentationSerializedBytes() {
        ItemStack reference = current();
        ItemStack old = historical(reference);
        ItemStack candidate = DoctorHistoricalStackRepair.prepare(old, reference, reference);
        assertNotNull(candidate);
        assertTrue(DoctorHistoricalStackRepair.preservesNonPresentationBytes(old, candidate));

        // Equal visible text cannot justify losing or rewriting typed addon data.
        ItemStack changed = new ItemStack(candidate);
        changed.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.INTEGER, 12345));
        assertFalse(DoctorHistoricalStackRepair.preservesNonPresentationBytes(old, changed));

        changed = new ItemStack(candidate);
        changed.setAmount(old.getAmount() - 1);
        assertFalse(DoctorHistoricalStackRepair.preservesNonPresentationBytes(old, changed));
        assertNotEquals(
                DoctorHistoricalStackRepair.fingerprint(old), DoctorHistoricalStackRepair.fingerprint(candidate));
    }

    @Test
    void refusesReferenceWhichDoesNotMatchTheRegisteredTemplate() {
        ItemStack template = current();
        ItemStack old = historical(template);
        ItemStack wrongReference = historical(template);
        wrongReference.setAmount(1);
        assertNull(DoctorHistoricalStackRepair.prepare(old, wrongReference, template));
    }

    @Test
    void doesNotApplyOrRetryARepairForAlreadyIdenticalItems() {
        ItemStack template = current();
        ItemStack reference = new ItemStack(template);
        assertNull(DoctorHistoricalStackRepair.prepare(reference, template, template));
    }

    @Test
    void doesNotInvokeHistoricalAddonCloneOverride() {
        ItemStack template = current();
        ItemStack legacy = historical(template);
        ItemStack old = new ItemStack(legacy) {
            @Override
            public ItemStack clone() {
                throw new AssertionError("Repair must not trigger addon clone hook");
            }
        };
        ItemStack result = DoctorHistoricalStackRepair.prepare(old, template, template);
        assertNotNull(result);
        assertTrue(result.isSimilar(template));
        assertEquals(37, result.getAmount());
    }

    @Test
    void backupIsExclusiveAndPreservesByteExactOriginal() throws Exception {
        ItemStack old = historical(current());
        byte[] originalBytes = old.serializeAsBytes();
        Path saved = DoctorHistoricalStackRepair.backup(folder, UUID.randomUUID(), originalBytes);
        assertTrue(Files.isRegularFile(saved));
        assertArrayEquals(originalBytes, Files.readAllBytes(saved));
        ItemStack recovered = ItemStack.deserializeBytes(Files.readAllBytes(saved));
        assertTrue(recovered.isSimilar(old));
        assertEquals(37, recovered.getAmount());
        assertEquals(DoctorHistoricalStackRepair.fingerprint(old), DoctorHistoricalStackRepair.fingerprint(recovered));
    }

    @Test
    void backupFailureNeverCreatesOrModifiesOriginalItem() throws Exception {
        ItemStack old = historical(current());
        byte[] original = old.serializeAsBytes();
        Files.write(folder.resolve("recovery"), new byte[] {0x21});
        assertThrows(
                java.io.IOException.class,
                () -> DoctorHistoricalStackRepair.backup(folder, UUID.randomUUID(), original));
        assertArrayEquals(original, old.serializeAsBytes());
        assertArrayEquals(new byte[] {0x21}, Files.readAllBytes(folder.resolve("recovery")));
    }

    @Test
    void refusesSymlinkedIdentityBackupDirectoryWithoutEscapingPluginData() throws Exception {
        ItemStack old = historical(current());
        byte[] original = old.serializeAsBytes();
        Path external = folder.resolve("external");
        Files.createDirectories(external);
        Files.createDirectories(folder.resolve("recovery"));
        try {
            Files.createSymbolicLink(folder.resolve("recovery/identity"), external);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            Assumptions.abort("Test environment does not support creating symbolic links");
        }
        assertThrows(
                java.io.IOException.class,
                () -> DoctorHistoricalStackRepair.backup(folder, UUID.randomUUID(), original));
        try (var entries = Files.list(external)) {
            assertEquals(0L, entries.count());
        }
        assertArrayEquals(original, old.serializeAsBytes());
    }

    @Test
    void persistedOwnerScopedReceiptVerifiesExactCandidateAcrossRestartWithoutItemMutation() throws Exception {
        ItemStack reference = current();
        ItemStack old = historical(reference);
        ItemStack repaired = DoctorHistoricalStackRepair.prepare(old, reference, reference);
        assertNotNull(repaired);
        UUID owner = UUID.randomUUID();
        Path saved = DoctorHistoricalStackRepair.backup(folder, owner, old.serializeAsBytes());
        String name = saved.getFileName().toString();
        UUID receipt = UUID.fromString(name.substring(owner.toString().length() + 1, name.length() - 4));

        ItemStack persisted = ItemStack.deserializeBytes(repaired.serializeAsBytes());
        ItemStack original = DoctorHistoricalStackRepair.readVerifiedBackup(folder, owner, receipt);
        assertArrayEquals(old.serializeAsBytes(), original.serializeAsBytes());
        assertTrue(DoctorHistoricalStackRepair.matchesSavedBackup(original, persisted, reference, reference));
        assertEquals(37, original.getAmount());
        assertEquals(37, persisted.getAmount());
        assertTrue(persisted.isSimilar(reference));
        assertFalse(original.isSimilar(reference));
        assertArrayEquals(old.serializeAsBytes(), Files.readAllBytes(saved));
    }

    @Test
    void persistedReceiptRejectsOtherPlayerChangedQuantityOrTypedMetadata() throws Exception {
        ItemStack reference = current();
        ItemStack original = historical(reference);
        ItemStack repaired = DoctorHistoricalStackRepair.prepare(original, reference, reference);
        assertNotNull(repaired);
        UUID owner = UUID.randomUUID();
        Path saved = DoctorHistoricalStackRepair.backup(folder, owner, original.serializeAsBytes());
        String name = saved.getFileName().toString();
        UUID receipt = UUID.fromString(name.substring(owner.toString().length() + 1, name.length() - 4));

        assertThrows(
                java.io.IOException.class,
                () -> DoctorHistoricalStackRepair.readVerifiedBackup(folder, UUID.randomUUID(), receipt));
        assertThrows(
                java.io.IOException.class,
                () -> DoctorHistoricalStackRepair.readVerifiedBackup(folder, owner, UUID.randomUUID()));
        ItemStack backed = DoctorHistoricalStackRepair.readVerifiedBackup(folder, owner, receipt);
        ItemStack altered = new ItemStack(repaired);
        altered.setAmount(repaired.getAmount() - 1);
        assertFalse(DoctorHistoricalStackRepair.matchesSavedBackup(backed, altered, reference, reference));
        altered = new ItemStack(repaired);
        altered.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.INTEGER, 12345));
        assertFalse(DoctorHistoricalStackRepair.matchesSavedBackup(backed, altered, reference, reference));
        ItemStack changedReference = historical(reference);
        changedReference.setAmount(1);
        assertFalse(DoctorHistoricalStackRepair.matchesSavedBackup(backed, repaired, changedReference, reference));
        assertArrayEquals(original.serializeAsBytes(), Files.readAllBytes(saved));
    }

    @Test
    void persistedReceiptRejectsCorruptBackupBytesAndLinkedBackupFiles() throws Exception {
        ItemStack old = historical(current());
        UUID owner = UUID.randomUUID();
        Path saved = DoctorHistoricalStackRepair.backup(folder, owner, old.serializeAsBytes());
        String name = saved.getFileName().toString();
        UUID receipt = UUID.fromString(name.substring(owner.toString().length() + 1, name.length() - 4));
        Files.write(saved, new byte[] {0x21, 0x22, 0x23});
        assertThrows(
                java.io.IOException.class,
                () -> DoctorHistoricalStackRepair.readVerifiedBackup(folder, owner, receipt));

        Path unrelated = folder.resolve("outside-original.bin");
        Files.write(unrelated, old.serializeAsBytes());
        Files.delete(saved);
        try {
            Files.createSymbolicLink(saved, unrelated);
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException exception) {
            Assumptions.abort("Test environment does not permit symbolic links");
        }
        assertThrows(
                java.io.IOException.class,
                () -> DoctorHistoricalStackRepair.readVerifiedBackup(folder, owner, receipt));
        assertArrayEquals(old.serializeAsBytes(), Files.readAllBytes(unrelated));
    }

    @Test
    void fingerprintIncludesAmountAndAllSerializedIdentity() {
        ItemStack item = historical(current());
        String prior = DoctorHistoricalStackRepair.fingerprint(item);
        item.setAmount(36);
        assertNotEquals(prior, DoctorHistoricalStackRepair.fingerprint(item));
        item.setAmount(37);
        assertEquals(prior, DoctorHistoricalStackRepair.fingerprint(item));
        item.editMeta(meta -> meta.getPersistentDataContainer()
                .set(new NamespacedKey("addon", "owner"), PersistentDataType.INTEGER, 12345));
        assertNotEquals(prior, DoctorHistoricalStackRepair.fingerprint(item));
    }
}
