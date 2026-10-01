package io.github.wickidcow.validation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import me.char321.sfadvancements.SFAdvancements;
import me.char321.sfadvancements.api.Advancement;
import me.char321.sfadvancements.api.AdvancementGroup;
import me.char321.sfadvancements.api.criteria.Criterion;
import me.char321.sfadvancements.api.reward.Reward;
import me.char321.sfadvancements.core.criteria.progress.PlayerProgress;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable real-server fixture: original writer, missing definitions, then usable restored progress. */
public final class AdvancementRetentionProbe extends JavaPlugin {
    private static final UUID FIRST = UUID.fromString("00000000-1111-2222-3333-444444444444");
    private static final UUID SECOND = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final NamespacedKey KNOWN = new NamespacedKey("compatfixture", "known");
    private static final NamespacedKey ABSENT = new NamespacedKey("compatfixture", "absent");
    private Criterion keep;
    private Criterion retired;
    private Criterion legacy;
    private int rewards;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            String mode = System.getProperty("advancement.mode");
            try {
                check(SFAdvancements.instance() != null && SFAdvancements.instance().isEnabled(), "Target addon is not enabled");
                check(!SFAdvancements.getMainConfig().getBoolean("use-advancements-api"), "Offline fixture must not invoke client API");
                check(!SFAdvancements.getMainConfig().getBoolean("announce-advancements"), "Offline fixture must not announce fake players");
                check(!SFAdvancements.getRegistry().getAdvancementGroups().isEmpty(), "Normal addon definitions did not load");
                AdvancementGroup group = SFAdvancements.getRegistry().getAdvancementGroups().getFirst();
                boolean missing = mode.equals("new-missing") || mode.equals("new-missing-restart") || mode.equals("old-missing-control");
                keep = criterion("keep", 100, KNOWN);
                retired = criterion("retired", 100, KNOWN);
                legacy = criterion("legacy", 37, ABSENT);
                register(group, KNOWN, missing ? new Criterion[]{keep} : new Criterion[]{keep, retired});
                if (!missing) register(group, ABSENT, new Criterion[]{legacy});

                PlayerProgress first = SFAdvancements.getAdvManager().getProgress(FIRST);
                PlayerProgress second = SFAdvancements.getAdvManager().getProgress(SECOND);
                check(first != second, "Separate player UUIDs share a progress object");
                if (mode.equals("old-produce")) {
                    perform(first, keep, 12);
                    perform(first, retired, 74);
                    perform(first, legacy, 37);
                    perform(second, keep, 5);
                    perform(second, retired, 23);
                    perform(second, legacy, 7);
                    check(rewards == 1, "Original completion must grant exactly one fixture reward");
                    check(first.isCompleted(ABSENT) && !second.isCompleted(ABSENT), "Original completion state is wrong");
                    check(first.getCriterionProgress(retired) == 74 && second.getCriterionProgress(retired) == 23, "Original criterion counts wrong");
                } else if (missing) {
                    int expectedFirst = mode.equals("new-missing-restart") ? 13 : 12;
                    int expectedSecond = mode.equals("new-missing-restart") ? 6 : 5;
                    check(first.getCriterionProgress(keep) == expectedFirst, "Known first-player progress changed before update");
                    check(second.getCriterionProgress(keep) == expectedSecond, "Known second-player progress changed before update");
                    check(!first.isCompleted(ABSENT) && !second.isCompleted(ABSENT), "Unavailable definitions must not be treated as active");
                    if (!mode.equals("new-missing-restart")) {
                        first.doCriterion(keep);
                        second.doCriterion(keep);
                    }
                    check(first.getCriterionProgress(keep) == 13 && second.getCriterionProgress(keep) == 6, "Normal active increments changed");
                    check(rewards == 0, "Loading or retaining missing data must not grant a reward");
                } else if (mode.equals("new-restore") || mode.equals("new-final")) {
                    check(first.getCriterionProgress(keep) == 13 && second.getCriterionProgress(keep) == 6, "Active updates did not survive restart");
                    check(first.getCriterionProgress(retired) == 74 && second.getCriterionProgress(retired) == 23, "Restored criterion did not recover its old counts");
                    check(first.getCriterionProgress(legacy) == 37, "Restored advancement lost completed counter");
                    int secondaryCount = mode.equals("new-restore") ? 7 : 8;
                    check(second.getCriterionProgress(legacy) == secondaryCount, "Restored second-player advancement lost partial progress");
                    check(first.isCompleted(ABSENT) && !second.isCompleted(ABSENT), "Restored completion flags changed");
                    first.doCriterion(legacy);
                    check(first.getCriterionProgress(legacy) == 37, "Already completed advancement must not progress or reward twice");
                    if (mode.equals("new-restore")) second.doCriterion(legacy);
                    check(second.getCriterionProgress(legacy) == 8, "Restored partial progress did not resume normally");
                    check(rewards == 0, "Restore/load must not duplicate completion rewards");
                } else {
                    throw new AssertionError("Unknown mode: " + mode);
                }
                first.save();
                second.save();
                if (!mode.equals("old-produce")) {
                    verifyStored(FIRST, mode);
                    verifyStored(SECOND, mode);
                }
                Files.writeString(Path.of("advancement-probe-result.txt"), "PASS\nmode=" + mode + "\nrewards=" + rewards + "\n");
                getLogger().info("ADVANCEMENT_RETENTION_PASS mode=" + mode + " rewards=" + rewards);
            } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "ADVANCEMENT_RETENTION_FAIL", failure);
                try {
                    Files.writeString(Path.of("advancement-probe-result.txt"), "FAIL\n" + failure + "\n");
                } catch (Exception ignored) {
                    getLogger().severe("Unable to write failure evidence");
                }
            } finally {
                Bukkit.shutdown();
            }
        }, 20L);
    }

    private Criterion criterion(String id, int count, NamespacedKey advancement) {
        Criterion criterion = new Criterion(id, count);
        criterion.setAdvancement(advancement);
        return criterion;
    }

    private void register(AdvancementGroup group, NamespacedKey key, Criterion[] criteria) {
        Reward reward = player -> rewards++;
        new Advancement(key, null, group, new ItemStack(Material.PAPER), key.toString(), false, criteria,
                new Reward[]{reward}).register();
    }

    private static void perform(PlayerProgress progress, Criterion criterion, int count) {
        for (int i = 0; i < count; i++) progress.doCriterion(criterion);
    }

    private static void verifyStored(UUID owner, String mode) throws Exception {
        File folder = new File(SFAdvancements.instance().getDataFolder(), "/advancements");
        JsonObject document = JsonParser.parseString(Files.readString(folder.toPath().resolve(owner + ".json"))).getAsJsonObject();
        JsonObject known = document.getAsJsonObject(KNOWN.toString());
        check(known != null, "Known record missing from real saved file");
        check(known.getAsJsonObject("criteria").get("keep").getAsInt() == (owner.equals(FIRST) ? 13 : 6), "Saved active counter wrong");
        if (mode.equals("old-missing-control")) {
            check(!document.has(ABSENT.toString()), "Original control must reproduce lost advancement");
            check(!known.getAsJsonObject("criteria").has("retired"), "Original control must reproduce lost criterion");
        } else {
            check(known.getAsJsonObject("criteria").get("retired").getAsInt() == (owner.equals(FIRST) ? 74 : 23), "Missing criterion was dropped");
            JsonObject absent = document.getAsJsonObject(ABSENT.toString());
            check(absent != null, "Missing advancement was dropped");
            check(absent.get("done").getAsBoolean() == owner.equals(FIRST), "Opaque completion flag changed");
            check(known.has("opaque-null") && known.get("opaque-null").isJsonNull(), "Unknown explicit null disappeared");
            check(known.getAsJsonObject("future").get("count").getAsLong() == Long.MAX_VALUE, "Unknown long value changed");
            check("<Exact Owner & State>".equals(known.getAsJsonObject("future").get("label").getAsString()), "Opaque text changed");
            check(document.has("opaque:external") && document.getAsJsonObject("opaque:external").get("unknown").isJsonNull(), "Unresolved external record lost");
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
