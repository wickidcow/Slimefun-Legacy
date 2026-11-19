package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestDoctorIdentityEvidenceExport {

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

    private static ItemStack carbonado() {
        ItemStack stack = new ItemStack(Material.PAPER, 1);
        stack.editMeta(meta -> {
            meta.displayName(Component.text("Carbonado"));
            meta.lore(List.of(Component.text("Black Diamond")));
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING, "CARBONADO");
            meta.getPersistentDataContainer()
                    .set(new NamespacedKey("addon", "private_owner"), PersistentDataType.LONG, 75123L);
        });
        return stack;
    }

    @Test
    void exportsBothActualHeldStacksWithExactBytesAndNoMutation() throws Exception {
        ItemStack cheat = carbonado();
        ItemStack oldShop = new ItemStack(cheat);
        oldShop.setAmount(63);
        oldShop.editMeta(meta -> meta.lore(List.of(Component.text("Old QuickShop Carbonado lore"))));
        byte[] cheatBytes = cheat.serializeAsBytes();
        byte[] oldBytes = oldShop.serializeAsBytes();
        UUID owner = UUID.randomUUID();

        var evidence = DoctorIdentityEvidenceExport.capture(folder, owner, cheat, oldShop);

        assertNotEquals(evidence.mainReceipt(), evidence.offhandReceipt());
        Path base = folder.resolve("recovery/identity");
        Path main = base.resolve(owner + "-" + evidence.mainReceipt() + ".bin");
        Path off = base.resolve(owner + "-" + evidence.offhandReceipt() + ".bin");
        assertArrayEquals(cheatBytes, Files.readAllBytes(main));
        assertArrayEquals(oldBytes, Files.readAllBytes(off));
        try (var listing = Files.list(base)) {
            assertEquals(2, listing.count());
        }
        assertEquals(1, cheat.getAmount());
        assertEquals(63, oldShop.getAmount());
        assertArrayEquals(cheatBytes, cheat.serializeAsBytes());
        assertArrayEquals(oldBytes, oldShop.serializeAsBytes());
    }

    @Test
    void rejectsEmptyHandsWithoutCreatingPrivateEvidence() {
        UUID owner = UUID.randomUUID();
        ItemStack old = carbonado();
        assertThrows(
                IOException.class,
                () -> DoctorIdentityEvidenceExport.capture(folder, owner, new ItemStack(Material.AIR), old));
        assertFalse(Files.exists(folder.resolve("recovery")));
    }

    @Test
    void refusesSymlinkedRecoveryDirectoryBeforeWritingAnything() throws Exception {
        Path outside = Files.createDirectory(folder.resolve("outside"));
        Path recovery = folder.resolve("recovery");
        Files.createSymbolicLink(recovery, outside);
        UUID owner = UUID.randomUUID();
        ItemStack main = carbonado();
        ItemStack off = carbonado();
        assertThrows(IOException.class, () -> DoctorIdentityEvidenceExport.capture(folder, owner, main, off));
        try (var files = Files.list(outside)) {
            assertEquals(0, files.count());
        }
    }
}
