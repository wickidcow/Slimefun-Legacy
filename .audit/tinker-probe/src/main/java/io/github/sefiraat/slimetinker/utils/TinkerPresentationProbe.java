package io.github.sefiraat.slimetinker.utils;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Native conversion comparisons only; not shipped in a maintained plugin. */
public final class TinkerPresentationProbe extends JavaPlugin {
    private int comparisons;

    @Override
    public void onEnable() {
        try {
            String implementation = fixture().getItemMeta().getClass().getName();
            require(implementation.contains("CraftMeta"), "Not a native ItemMeta: " + implementation);
            compareGenerated();
            preserveExisting();
            require(comparisons == 1002, "Incorrect comparison count: " + comparisons);
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve("native-presentation.txt"),
                    "PASS native generated-presentation comparisons=" + comparisons + "\n"
                    + "Implementation=" + implementation + "\n"
                    + "Actual old native setters compared with new generated components; rich existing lore and typed PDC retained.\n"
                    + "This does not execute complete material/level/modifier gameplay or a captured old player world.\n");
            getLogger().info("TINKER_NATIVE_PRESENTATION_PASS comparisons=1002");
        } catch (Throwable failure) {
            getLogger().log(java.util.logging.Level.SEVERE, "TINKER_NATIVE_PRESENTATION_FAIL", failure);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @SuppressWarnings("deprecation") // Deliberately compare the historical native setters; never production code.
    private void compareGenerated() {
        Random random = new Random(349194);
        String[] styles = {"", "§7", "§a", "§l", "§r§f", "§x§c§2§f§c§0§3", "§x§e§4§e§d§3§2"};
        for (int sample = 0; sample < 500; sample++) {
            ItemStack original = fixture();
            ItemStack oldItem = original.clone();
            ItemStack newItem = original.clone();
            String name = styles[random.nextInt(styles.length)] + "Iron-" + sample
                    + "-" + styles[random.nextInt(styles.length)] + "Binding §fPickaxe";
            ItemMeta oldMeta = oldItem.getItemMeta(); ItemMeta newMeta = newItem.getItemMeta();
            oldMeta.setDisplayName(name); newMeta.displayName(ItemPresentation.generatedName(name));
            require(oldItem.setItemMeta(oldMeta) && newItem.setItemMeta(newMeta), "Native name commit rejected");
            require(oldItem.equals(newItem), "Generated name differs from historical native setter at case " + sample);
            protectedMetadata(original, newItem);
            comparisons++;

            List<String> lines = new ArrayList<>();
            for (int line = 0, size = random.nextInt(25); line < size; line++) {
                lines.add(styles[random.nextInt(styles.length)] + (line % 5 == 0 ? "" : "Level: " + random.nextInt(10000)
                        + " (" + random.nextInt(10000) + " / 25000)"));
            }
            oldMeta = oldItem.getItemMeta(); newMeta = newItem.getItemMeta();
            oldMeta.setLore(lines); newMeta.lore(ItemPresentation.generatedLore(lines));
            require(oldItem.setItemMeta(oldMeta) && newItem.setItemMeta(newMeta), "Native lore commit rejected");
            require(oldItem.equals(newItem), "Generated lore differs from historical native setter at case " + sample
                    + " expected=" + oldItem.getItemMeta() + " actual=" + newItem.getItemMeta());
            protectedMetadata(original, newItem);
            comparisons++;
        }
    }

    @SuppressWarnings("deprecation") // Historical theme append control on a disposable native fixture.
    private void preserveExisting() {
        ItemStack original = fixture();
        ItemStack result = original.clone();
        ItemMeta meta = result.getItemMeta();
        List<Component> before = meta.lore();
        require(before != null && before.size() == 2, "Missing native rich fixture lore");
        meta.lore(ItemPresentation.themedLore(meta.lore(), Arrays.asList("Generated", null, ""), "§7", "§ePart"));
        require(result.setItemMeta(meta), "Theme append rejected");
        require(result.getItemMeta().lore().subList(0, before.size()).equals(before), "Existing rich lore flattened");
        protectedMetadata(original, result);
        ItemStack restored = ItemStack.deserializeBytes(result.serializeAsBytes());
        require(restored.equals(result), "Theme result changed during native persistence round trip");
        comparisons++;

        ItemStack oldItem = new ItemStack(Material.PAPER);
        ItemStack newItem = oldItem.clone();
        ItemMeta oldMeta = oldItem.getItemMeta(); ItemMeta newMeta = newItem.getItemMeta();
        List<String> oldLore = oldMeta.getLore();
        if (oldLore == null) oldLore = new ArrayList<>();
        for (String line : Arrays.asList("Generated", null, "")) oldLore.add("§7" + line);
        oldLore.add(""); oldLore.add("§ePart"); oldMeta.setLore(oldLore);
        newMeta.lore(ItemPresentation.themedLore(newMeta.lore(), Arrays.asList("Generated", null, ""), "§7", "§ePart"));
        require(oldItem.setItemMeta(oldMeta) && newItem.setItemMeta(newMeta), "Theme fallback commit rejected");
        require(oldItem.equals(newItem), "Absent-lore theme layout differs from old native behavior");
        comparisons++;
    }

    private ItemStack fixture() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Original player name", NamedTextColor.BLUE));
        meta.lore(List.of(Component.text("Original rich description", NamedTextColor.AQUA)
                .hoverEvent(HoverEvent.showText(Component.text("Original hover"))).insertion("Original insertion"), Component.empty()));
        meta.getPersistentDataContainer().set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "ORIGINAL_TINKER_ITEM");
        meta.getPersistentDataContainer().set(key("slimetinker:tool_info_head_material"), PersistentDataType.STRING, "IRON");
        meta.getPersistentDataContainer().set(key("slimetinker:st_level"), PersistentDataType.INTEGER, 42);
        meta.getPersistentDataContainer().set(key("slimetinker:st_exp_required"), PersistentDataType.DOUBLE, 12345.6789D);
        meta.getPersistentDataContainer().set(key("legacyaddon:owner_uuid"), PersistentDataType.STRING, "11111111-2222-3333-4444-555555555555");
        meta.getPersistentDataContainer().set(key("legacyaddon:opaque"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42});
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true); meta.setUnbreakable(true); ((Damageable) meta).setDamage(127);
        var model = meta.getCustomModelDataComponent(); model.setFloats(List.of(9876F, 0.5F)); model.setStrings(List.of("unchanged_model"));
        meta.setCustomModelDataComponent(model);
        require(item.setItemMeta(meta), "Fixture metadata rejected"); return item;
    }

    private static void protectedMetadata(ItemStack expected, ItemStack actual) {
        require(expected.getType() == actual.getType() && expected.getAmount() == actual.getAmount(), "Type/amount changed");
        ItemMeta a = expected.getItemMeta(); ItemMeta b = actual.getItemMeta();
        a.displayName(null); b.displayName(null); a.lore(null); b.lore(null);
        require(a.equals(b), "Protected native metadata changed");
    }
    private static NamespacedKey key(String value) { return Objects.requireNonNull(NamespacedKey.fromString(value)); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
