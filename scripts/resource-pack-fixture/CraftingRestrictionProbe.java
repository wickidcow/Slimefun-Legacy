package io.github.wickidcow.validation;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.CraftingRestrictionService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/** Checks configuration lifecycle only in the disposable native server. */
final class CraftingRestrictionProbe {
    private CraftingRestrictionProbe() {}

    static void verify() throws Exception {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("Slimefun");
        if (plugin == null || !plugin.isEnabled()) throw new AssertionError("Slimefun not enabled");
        Path file = plugin.getDataFolder().toPath().resolve("CraftingRestrictions.yml");
        // Do not call reload or saveResource first: startup itself must have created the file.
        if (!Files.isRegularFile(file)) throw new AssertionError("Startup did not create CraftingRestrictions.yml");
        byte[] original = Files.readAllBytes(file);
        try (InputStream resource = plugin.getResource("CraftingRestrictions.yml")) {
            if (resource == null || !Arrays.equals(original, resource.readAllBytes())) {
                throw new AssertionError("Fresh configuration differs from packaged default");
            }
        }
        CraftingRestrictionService service = Slimefun.getCraftingRestrictionService();
        SlimefunItem wire = SlimefunItem.getById("COPPER_WIRE");
        if (wire == null) throw new AssertionError("Missing registered COPPER_WIRE");
        byte[] template = wire.getItem().serializeAsBytes();
        try {
            String policy = "enabled: true\nitems:\n  COPPER_WIRE:\n    crafting-enabled: false\n";
            Files.writeString(file, policy);
            if (!service.reload() || !service.isBlocked(wire.getItem()) || service.isBlocked("COPPER_INGOT")) {
                throw new AssertionError("Custom restrictions were not loaded by registered identity");
            }
            if (!policy.equals(Files.readString(file))) throw new AssertionError("Reload overwrote owner policy");
            Files.writeString(file, "items: [invalid]\n");
            if (service.reload() || !service.isBlocked(wire.getItem())) {
                throw new AssertionError("Malformed reload discarded last valid policy");
            }
            if (!"items: [invalid]\n".equals(Files.readString(file))) {
                throw new AssertionError("Malformed owner configuration was rewritten");
            }
            if (!Arrays.equals(template, wire.getItem().serializeAsBytes())) {
                throw new AssertionError("Policy inspection changed registered template");
            }
        } finally {
            Files.write(file, original);
            if (!service.reload() || !Arrays.equals(original, Files.readAllBytes(file))) {
                throw new AssertionError("Unable to restore disposable server policy");
            }
        }
        if (service.isBlocked("COPPER_WIRE")) throw new AssertionError("Probe left wire crafting blocked");
        plugin.getLogger().info("CraftingRestrictions native lifecycle PASS: startup, reload, malformed retention, restore");
    }
}
