package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestBackpackOpenRegistry {

    @Test
    void duplicateBackpackReservationDoesNotReleaseOriginalPlayer() {
        BackpackOpenRegistry registry = new BackpackOpenRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        Assertions.assertTrue(registry.reserve(first, "uuid:backpack"));
        Assertions.assertFalse(registry.reserve(second, "uuid:backpack"));
        Assertions.assertTrue(registry.isOpening(first));
        Assertions.assertFalse(registry.isOpening(second));

        registry.release(first, "uuid:backpack");
        Assertions.assertTrue(registry.reserve(second, "uuid:backpack"));
    }

    @Test
    void wrongReservationKeyCannotUnlockBackpack() {
        BackpackOpenRegistry registry = new BackpackOpenRegistry();
        UUID player = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Assertions.assertTrue(registry.reserve(player, "uuid:one"));
        registry.release(player, "uuid:two");
        Assertions.assertTrue(registry.isOpening(player));
        Assertions.assertFalse(registry.reserve(player, "uuid:two"));
        Assertions.assertFalse(registry.reserve(other, "uuid:one"));
    }

    @Test
    void activatedReservationRemainsExclusiveUntilClose() {
        BackpackOpenRegistry registry = new BackpackOpenRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        Assertions.assertTrue(registry.reserve(first, "uuid:backpack"));
        Assertions.assertTrue(registry.activate(first, "uuid:backpack", "uuid:backpack"));
        Assertions.assertFalse(registry.isOpening(first));

        // The load is finished, but the backing backpack is still actively open.
        Assertions.assertFalse(registry.reserve(second, "uuid:backpack"));
        Assertions.assertFalse(registry.reserve(first, "uuid:other"));

        registry.release(first);
        Assertions.assertTrue(registry.reserve(second, "uuid:backpack"));
    }

    @Test
    void legacyReservationPromotesToCanonicalUuid() {
        BackpackOpenRegistry registry = new BackpackOpenRegistry();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        Assertions.assertTrue(registry.reserve(first, "legacy:owner:18"));
        Assertions.assertTrue(registry.activate(first, "legacy:owner:18", "uuid:canonical"));
        Assertions.assertFalse(registry.isOpening(first));

        // A modern item representation of the same backing backpack must now be blocked.
        Assertions.assertFalse(registry.reserve(second, "uuid:canonical"));
        // The temporary legacy lookup key was replaced, so it is no longer globally locked.
        Assertions.assertTrue(registry.reserve(second, "legacy:owner:18"));
    }

    @Test
    void canonicalCollisionDoesNotStealExistingSession() {
        BackpackOpenRegistry registry = new BackpackOpenRegistry();
        UUID legacyPlayer = UUID.randomUUID();
        UUID modernPlayer = UUID.randomUUID();
        UUID thirdPlayer = UUID.randomUUID();

        Assertions.assertTrue(registry.reserve(legacyPlayer, "legacy:owner:18"));
        Assertions.assertTrue(registry.reserve(modernPlayer, "uuid:canonical"));

        // The mixed legacy representation resolves to a UUID already owned by the modern request.
        Assertions.assertFalse(registry.activate(legacyPlayer, "legacy:owner:18", "uuid:canonical"));
        Assertions.assertTrue(registry.isOpening(legacyPlayer));

        registry.release(legacyPlayer);
        Assertions.assertTrue(registry.activate(modernPlayer, "uuid:canonical", "uuid:canonical"));
        Assertions.assertFalse(registry.reserve(thirdPlayer, "uuid:canonical"));

        registry.release(modernPlayer);
        Assertions.assertTrue(registry.reserve(thirdPlayer, "uuid:canonical"));
    }
}
