package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class LiveItemIdentityEvidenceTest {

    @Test
    void compareUsesDetachedOneItemCopiesWithoutChangingHistoricalAmounts() {
        ItemStack historical = mock(ItemStack.class);
        ItemStack current = mock(ItemStack.class);
        ItemStack oldCopy = mock(ItemStack.class);
        ItemStack newCopy = mock(ItemStack.class);
        when(historical.clone()).thenReturn(oldCopy);
        when(current.clone()).thenReturn(newCopy);
        when(oldCopy.getType()).thenReturn(Material.PLAYER_HEAD);
        when(newCopy.getType()).thenReturn(Material.PLAYER_HEAD);
        when(oldCopy.isSimilar(newCopy)).thenReturn(false);

        LiveItemIdentityEvidence.Comparison evidence = LiveItemIdentityEvidence.compare(historical, current);

        verify(oldCopy).setAmount(1);
        verify(newCopy).setAmount(1);
        org.mockito.Mockito.verify(historical, org.mockito.Mockito.never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.verify(current, org.mockito.Mockito.never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        assertTrue(evidence.sameMaterial());
        assertFalse(evidence.bukkitSimilar());
        assertTrue(evidence.requiresFurtherInspection());
    }

    @Test
    void pdcKeyAgreementIsNeverAnAuthorizationToRewrite() {
        LiveItemIdentityEvidence.Comparison evidence =
                new LiveItemIdentityEvidence.Comparison(true, false, false, true, Set.of("slimefun:id"), Set.of("slimefun:id"));

        assertTrue(evidence.requiresFurtherInspection());
        assertTrue(evidence.samePdcKeyNames());
    }

    @Test
    void diagnosticPdcNamesCannotBeChangedByConsumers() {
        LiveItemIdentityEvidence.Comparison evidence =
                new LiveItemIdentityEvidence.Comparison(true, true, true, true, Set.of("slimefun:id"), Set.of("slimefun:id"));

        assertThrows(UnsupportedOperationException.class, () -> evidence.historicalPdcKeyNames().add("owner:key"));
    }
}
