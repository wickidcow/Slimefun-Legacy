package io.github.thebusybiscuit.slimefun4.implementation.items.magic8ball;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** The interactive item from the historical Magic 8 Ball addon. */
public final class Magic8BallItem extends SlimefunItem implements Listener {

    private static final List<String> AFFIRMATIVE = List.of(
            "It is certain",
            "It is decidedly so",
            "Without a doubt",
            "Yes definitely",
            "You may rely on it",
            "As I see it, yes",
            "Most likely",
            "Outlook good",
            "Yes",
            "Signs point to yes");
    private static final List<String> NONCOMMITTAL = List.of(
            "Reply hazy, try again",
            "Ask again later",
            "Better not tell you now",
            "Cannot predict now",
            "Concentrate and ask again");
    private static final List<String> NEGATIVE = List.of(
            "Don’t count on it", "My reply is no", "My sources say no", "Outlook not so good", "Very doubtful");

    public Magic8BallItem(
            ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onPlayerInteractBlock());
    }

    @Override
    public void preRegister() {
        Slimefun plugin = Slimefun.instance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR || event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        SlimefunItem itemHeld = SlimefunItem.getByItem(event.getItem());
        if (itemHeld != null && itemHeld.getId().equals(getId())) {
            sendRandomAnswer(event.getPlayer(), null);
        }
    }

    private BlockUseHandler onPlayerInteractBlock() {
        return event -> {
            Player player = event.getPlayer();
            Optional<Block> block = event.getClickedBlock();
            block.ifPresent(value -> sendRandomAnswer(player, value));
        };
    }

    private void sendRandomAnswer(@Nonnull Player player, @Nullable Block block) {
        Answer answer = randomAnswer();
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(answer.color() + answer.message()));
        player.getWorld().playSound(player.getLocation(), answer.sound(), 1, 0.65F);

        Location particleLocation = block == null ? getHandLocation(player) : block.getLocation();
        World world = block == null ? player.getWorld() : block.getWorld();
        world.spawnParticle(answer.particle(), particleLocation, 20);
    }

    private static Answer randomAnswer() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return switch (random.nextInt(3)) {
            case 0 -> new Answer(
                    "§a",
                    AFFIRMATIVE.get(random.nextInt(AFFIRMATIVE.size())),
                    Sound.ENTITY_VILLAGER_YES,
                    Particle.TOTEM_OF_UNDYING);
            case 1 -> new Answer(
                    "§7",
                    NONCOMMITTAL.get(random.nextInt(NONCOMMITTAL.size())),
                    Sound.ENTITY_VILLAGER_TRADE,
                    Particle.CRIT);
            default -> new Answer(
                    "§c",
                    NEGATIVE.get(random.nextInt(NEGATIVE.size())),
                    Sound.ENTITY_VILLAGER_NO,
                    VersionedParticle.ENCHANTED_HIT);
        };
    }

    private static Location getHandLocation(Player player) {
        Location location = player.getEyeLocation().clone();
        float angle = location.getYaw() / 60;
        location.subtract(new Vector(Math.cos(angle), 0, Math.sin(angle)).normalize().multiply(0.325));
        location.subtract(0, 0.7, 0);
        return location.add(new Vector(
                        -Math.sin(Math.toRadians(location.getYaw())), 0, Math.cos(Math.toRadians(location.getYaw())))
                .normalize()
                .multiply(0.6));
    }

    private record Answer(String color, String message, Sound sound, Particle particle) {}
}
