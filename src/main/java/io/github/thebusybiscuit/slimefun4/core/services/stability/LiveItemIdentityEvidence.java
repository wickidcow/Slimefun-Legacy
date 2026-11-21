package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

/**
 * Read-only, conservative identity evidence for the live historical-item Doctor.
 *
 * <p>This deliberately does not decide that two items may be substituted or repaired. In
 * particular, equal Slimefun IDs and matching visible text do not prove that typed PDC,
 * ownership, inventory contents, or other functional components are interchangeable.
 */
public final class LiveItemIdentityEvidence {

    private LiveItemIdentityEvidence() {}

    /**
     * Compare detached one-item copies. Neither input nor its quantity is modified.
     *
     * <p>The key sets are safe to report, but the values must never be included in logs.
     */
    public static @Nonnull Comparison compare(@Nonnull ItemStack historical, @Nonnull ItemStack current) {
        ItemStack oldCopy = historical.clone();
        ItemStack newCopy = current.clone();
        oldCopy.setAmount(1);
        newCopy.setAmount(1);

        ItemMeta oldMeta = oldCopy.getItemMeta();
        ItemMeta newMeta = newCopy.getItemMeta();

        Set<String> oldKeys = keyNames(oldMeta);
        Set<String> newKeys = keyNames(newMeta);

        return new Comparison(
                oldCopy.getType() == newCopy.getType(),
                oldCopy.isSimilar(newCopy),
                oldMeta != null && newMeta != null && oldMeta.equals(newMeta),
                oldKeys.equals(newKeys),
                oldKeys,
                newKeys);
    }

    private static Set<String> keyNames(ItemMeta meta) {
        if (meta == null) {
            return Collections.emptySet();
        }
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Set<String> names = new HashSet<>();
        data.getKeys().forEach(key -> names.add(key.toString()));
        return Collections.unmodifiableSet(names);
    }

    /** Diagnostic-only: not a repair authorization or a semantic Slimefun identity verdict. */
    public record Comparison(
            boolean sameMaterial,
            boolean bukkitSimilar,
            boolean equalItemMeta,
            boolean samePdcKeyNames,
            Set<String> historicalPdcKeyNames,
            Set<String> currentPdcKeyNames) {

        public boolean requiresFurtherInspection() {
            return !sameMaterial || !bukkitSimilar || !equalItemMeta || !samePdcKeyNames;
        }
    }
}
