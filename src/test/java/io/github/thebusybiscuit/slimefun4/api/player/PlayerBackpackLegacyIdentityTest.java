package io.github.thebusybiscuit.slimefun4.api.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlayerBackpackLegacyIdentityTest {

    @Test
    void legacyIdentityIncludesOwnerUuidAndBackpackId() {
        UUID owner = UUID.randomUUID();

        assertEquals(
                Optional.of(owner + ":18"),
                PlayerBackpack.getLegacyBackpackIdentity(List.of("\u00A77ID: " + owner + "#18")));
    }

    @Test
    void sameLegacyIdFromDifferentOwnersDoesNotCollide() {
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();

        Optional<String> first =
                PlayerBackpack.getLegacyBackpackIdentity(List.of("\u00A77ID: " + firstOwner + "#18"));
        Optional<String> second =
                PlayerBackpack.getLegacyBackpackIdentity(List.of("\u00A77ID: " + secondOwner + "#18"));

        assertNotEquals(first, second);
    }

    @Test
    void malformedLegacyIdentityIsRejected() {
        assertEquals(
                Optional.empty(),
                PlayerBackpack.getLegacyBackpackIdentity(List.of("\u00A77ID: not-a-uuid#18")));
    }
}
