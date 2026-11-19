package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Read-only comparison of a traversed item against its currently registered template.
 *
 * <p>A cosmetic-only result is a triage candidate, NOT repair eligibility. Addons may
 * intentionally use dynamic text; no item, registered template, or persistent data is
 * rewritten, and this comparison does not validate a machine or Cargo producer.
 */
final class HistoricalItemIdentityScanner {

    enum Result {
        MATCH,
        PRESENTATION_ONLY_CANDIDATE,
        BLANK_LORE_LAYOUT_DIFFERENCE,
        OTHER_DIFFERENCE,
        TEMPLATE_UNAVAILABLE
    }

    private HistoricalItemIdentityScanner() {}

    static Result inspect(ItemStack actual, @Nullable ItemStack registeredTemplate) {
        if (registeredTemplate == null || registeredTemplate.getType().isAir()) {
            return Result.TEMPLATE_UNAVAILABLE;
        }
        // Bukkit's ItemStack copy constructor can delegate to an overridden
        // clone() on addon subclasses, which might rewrite their presentation.
        // Round-trip exact native item bytes instead; nothing is written back.
        ItemStack actualCopy = exactNativeSnapshot(actual);
        ItemStack templateCopy = exactNativeSnapshot(registeredTemplate);
        // Never compare arbitrary addon ItemStack subclasses directly with
        // CraftItemStack.isSimilar. The raw source may use an overridden
        // clone/isSimilar hook or carry opaque data Bukkit cannot round-trip.
        if (actualCopy == null || templateCopy == null) {
            return Result.TEMPLATE_UNAVAILABLE;
        }
        if (actualCopy.isSimilar(templateCopy)) {
            return Result.MATCH;
        }
        if (actualCopy.getType() != templateCopy.getType()) {
            return Result.OTHER_DIFFERENCE;
        }
        ItemMeta actualMeta = actualCopy.getItemMeta();
        ItemMeta templateMeta = templateCopy.getItemMeta();
        if (actualMeta == null
                || templateMeta == null
                || !Objects.equals(plain(actualMeta.displayName()), plain(templateMeta.displayName()))) {
            return Result.OTHER_DIFFERENCE;
        }
        List<String> actualLore = plainLore(actualMeta.lore());
        List<String> templateLore = plainLore(templateMeta.lore());
        boolean sameLore = Objects.equals(actualLore, templateLore);
        boolean blankLoreOnly = !sameLore && sameNonblankLore(actualLore, templateLore);
        if (!sameLore && !blankLoreOnly) {
            return Result.OTHER_DIFFERENCE;
        }

        // Strip *only* text from detached snapshots. Strict Bukkit comparison
        // still protects typed PDC, item-model, skull profile, attributes, etc.
        actualCopy.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        templateCopy.editMeta(meta -> {
            meta.displayName(null);
            meta.lore(null);
        });
        // A registered item is normally a one-item template, while old storage
        // stacks may contain more. Stack identity ignores quantity.
        // Normalize only the detached template amount before strict byte comparison.
        templateCopy.setAmount(actualCopy.getAmount());
        if (!Arrays.equals(actualCopy.serializeAsBytes(), templateCopy.serializeAsBytes())) {
            return Result.OTHER_DIFFERENCE;
        }
        // A diagnostic classification, not permission to mutate old shop
        // templates, storage or chests. The actual nonblank text is unchanged.
        return blankLoreOnly ? Result.BLANK_LORE_LAYOUT_DIFFERENCE : Result.PRESENTATION_ONLY_CANDIDATE;
    }

    private static boolean sameNonblankLore(List<String> actual, List<String> registered) {
        if (actual == null || registered == null) {
            return false;
        }
        List<String> oldMeaningful =
                actual.stream().filter(line -> line != null && !line.isBlank()).toList();
        List<String> newMeaningful = registered.stream()
                .filter(line -> line != null && !line.isBlank())
                .toList();
        return !oldMeaningful.isEmpty() && oldMeaningful.equals(newMeaningful);
    }

    private static @Nullable ItemStack exactNativeSnapshot(ItemStack item) {
        try {
            byte[] original = item.serializeAsBytes();
            ItemStack detached = ItemStack.deserializeBytes(original);
            return item.getType() == detached.getType()
                            && item.getAmount() == detached.getAmount()
                            && Arrays.equals(original, detached.serializeAsBytes())
                    ? detached
                    : null;
        } catch (RuntimeException exception) {
            // Missing addon serializer, malformed native bytes or unknown future
            // components must not produce a repair-eligible scan result.
            return null;
        }
    }

    private static String plain(Component component) {
        return component == null
                ? null
                : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plainLore(List<Component> lore) {
        return lore == null
                ? null
                : lore.stream().map(HistoricalItemIdentityScanner::plain).toList();
    }
}
