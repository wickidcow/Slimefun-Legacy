package io.github.thebusybiscuit.slimefun4.core.services.stability;

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
        ItemStack actualCopy = ItemStack.deserializeBytes(actual.serializeAsBytes());
        ItemStack templateCopy = ItemStack.deserializeBytes(registeredTemplate.serializeAsBytes());
        // A future Bukkit/addon serializer may omit identity-bearing components.
        // Refuse classification unless BOTH detached copies retain strict identity.
        if (!actual.isSimilar(actualCopy) || !registeredTemplate.isSimilar(templateCopy)) {
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
        if (actualMeta == null || templateMeta == null
                || !Objects.equals(plain(actualMeta.displayName()), plain(templateMeta.displayName()))
                || !Objects.equals(plainLore(actualMeta.lore()), plainLore(templateMeta.lore()))) {
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
        return actualCopy.isSimilar(templateCopy)
                ? Result.PRESENTATION_ONLY_CANDIDATE
                : Result.OTHER_DIFFERENCE;
    }

    private static String plain(Component component) {
        return component == null ? null : PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<String> plainLore(List<Component> lore) {
        return lore == null ? null : lore.stream().map(HistoricalItemIdentityScanner::plain).toList();
    }
}
