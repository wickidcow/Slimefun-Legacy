package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactive;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * A reusable field trap that can safely transport one radioactive dropped ItemStack.
 */
public final class ContainmentTrap extends SimpleSlimefunItem<ItemUseHandler> {

    private static final double CAPTURE_RADIUS = 3.5D;
    private static final int MAX_CAPTURE_ATTEMPTS = 8;
    private static final long INITIAL_SCAN_DELAY = 8L;
    private static final long SCAN_PERIOD = 5L;

    private final NamespacedKey payloadKey = new NamespacedKey(Slimefun.instance(), "containment_trap_payload");

    @ParametersAreNonnullByDefault
    public ContainmentTrap(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack trap = event.getItem();

            if (hasPayload(trap)) {
                releasePayload(player, trap);
                return;
            }

            throwTrap(player, event.getHand(), trap);
        };
    }

    private void throwTrap(Player player, EquipmentSlot hand, ItemStack heldTrap) {
        ItemStack thrownStack = heldTrap.clone();
        thrownStack.setAmount(1);
        ensureSingleStack(thrownStack);

        Location origin = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.45D));
        Item thrown = player.getWorld().dropItem(origin, thrownStack);
        Vector velocity = player.getLocation().getDirection().normalize().multiply(0.85D);
        velocity.setY(velocity.getY() + 0.10D);
        thrown.setVelocity(velocity);
        thrown.setPickupDelay(30);
        thrown.setGlowing(true);

        if (player.getGameMode() != GameMode.CREATIVE) {
            consumeOne(player, hand);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.65F, 1.3F);

        AtomicInteger attempts = new AtomicInteger();
        thrown.getScheduler().runAtFixedRate(
                Slimefun.instance(),
                task -> {
                    if (!thrown.isValid()) {
                        task.cancel();
                        return;
                    }

                    thrown.getWorld().spawnParticle(
                            Particle.ELECTRIC_SPARK,
                            thrown.getLocation().add(0.0D, 0.15D, 0.0D),
                            3,
                            0.22D,
                            0.12D,
                            0.22D,
                            0.01D);

                    if (captureNearestRadioactiveItem(thrown)) {
                        task.cancel();
                        return;
                    }

                    if (attempts.incrementAndGet() >= MAX_CAPTURE_ATTEMPTS) {
                        thrown.setGlowing(false);
                        thrown.setPickupDelay(0);
                        task.cancel();
                    }
                },
                null,
                INITIAL_SCAN_DELAY,
                SCAN_PERIOD);
    }

    private boolean captureNearestRadioactiveItem(Item trapEntity) {
        Item target = trapEntity.getNearbyEntities(CAPTURE_RADIUS, 2.5D, CAPTURE_RADIUS).stream()
                .filter(Item.class::isInstance)
                .map(Item.class::cast)
                .filter(Entity::isValid)
                .filter(item -> item.getUniqueId() != trapEntity.getUniqueId())
                .filter(item -> isRadioactive(item.getItemStack()))
                .min(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(trapEntity.getLocation())))
                .orElse(null);

        if (target == null) {
            return false;
        }

        ItemStack radioactiveStack = target.getItemStack().clone();
        ItemStack filledTrap = trapEntity.getItemStack().clone();
        if (!storePayload(filledTrap, radioactiveStack)) {
            return false;
        }

        target.remove();
        trapEntity.setItemStack(filledTrap);
        trapEntity.setGlowing(false);
        trapEntity.setPickupDelay(10);

        Location location = trapEntity.getLocation();
        trapEntity.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, location, 18, 0.45D, 0.30D, 0.45D, 0.03D);
        trapEntity.getWorld().spawnParticle(Particle.END_ROD, location, 10, 0.25D, 0.22D, 0.25D, 0.01D);
        trapEntity.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 0.75F, 1.7F);
        trapEntity.getWorld().playSound(location, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8F, 0.75F);
        return true;
    }

    private boolean isRadioactive(ItemStack stack) {
        SlimefunItem item = SlimefunItem.getByItem(stack);
        return item instanceof Radioactive;
    }

    private boolean storePayload(ItemStack trap, ItemStack payload) {
        ItemMeta meta = trap.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(payloadKey, PersistentDataType.BYTE_ARRAY)) {
            return false;
        }

        pdc.set(payloadKey, PersistentDataType.BYTE_ARRAY, payload.serializeAsBytes());
        meta.setDisplayName(ChatColor.GOLD + "Containment Trap " + ChatColor.YELLOW + "[SEALED]");
        meta.setLore(List.of(
                ChatColor.GRAY + "Radioactive material securely contained.",
                ChatColor.YELLOW + friendlyName(payload),
                "",
                ChatColor.GREEN + "Right Click " + ChatColor.GRAY + "to release contents"));
        meta.setMaxStackSize(1);
        trap.setItemMeta(meta);
        return true;
    }

    private void releasePayload(Player player, ItemStack trap) {
        ItemMeta meta = trap.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        byte[] payloadBytes = pdc.get(payloadKey, PersistentDataType.BYTE_ARRAY);
        if (payloadBytes == null) {
            return;
        }

        final ItemStack payload;
        try {
            payload = ItemStack.deserializeBytes(payloadBytes);
        } catch (RuntimeException ex) {
            player.sendMessage(ChatColor.RED + "This Containment Trap could not safely decode its stored item.");
            return;
        }

        pdc.remove(payloadKey);
        meta.setDisplayName(ChatColor.GOLD + "Containment Trap");
        meta.setLore(emptyLore());
        meta.setMaxStackSize(1);
        trap.setItemMeta(meta);

        player.getWorld().dropItemNaturally(player.getLocation().add(0.0D, 0.5D, 0.0D), payload);
        player.getWorld().spawnParticle(
                Particle.ELECTRIC_SPARK,
                player.getLocation().add(0.0D, 0.8D, 0.0D),
                12,
                0.45D,
                0.35D,
                0.45D,
                0.02D);
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_OPEN, 0.75F, 0.8F);
        player.sendMessage(ChatColor.GOLD + "Containment Trap opened: " + ChatColor.YELLOW + friendlyName(payload));
    }

    private boolean hasPayload(ItemStack trap) {
        return trap.hasItemMeta()
                && trap.getItemMeta()
                        .getPersistentDataContainer()
                        .has(payloadKey, PersistentDataType.BYTE_ARRAY);
    }

    private static void consumeOne(Player player, EquipmentSlot hand) {
        ItemStack held = hand == EquipmentSlot.OFF_HAND
                ? player.getInventory().getItemInOffHand()
                : player.getInventory().getItemInMainHand();

        if (held.getAmount() <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            }
        } else {
            held.setAmount(held.getAmount() - 1);
        }
    }

    public static void ensureSingleStack(ItemStack trap) {
        ItemMeta meta = trap.getItemMeta();
        meta.setMaxStackSize(1);
        trap.setItemMeta(meta);
    }

    public static List<String> emptyLore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A reusable field trap for dangerous cargo.");
        lore.add(ChatColor.GRAY + "It can safely seal one dropped radioactive stack.");
        lore.add("");
        lore.add(ChatColor.YELLOW + "Right Click " + ChatColor.GRAY + "to throw");
        lore.add(ChatColor.DARK_GRAY + "Lands near radioactive material to capture it");
        return lore;
    }

    private static String friendlyName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            return ChatColor.stripColor(stack.getItemMeta().getDisplayName());
        }

        String[] words = stack.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return name.toString();
    }
}
