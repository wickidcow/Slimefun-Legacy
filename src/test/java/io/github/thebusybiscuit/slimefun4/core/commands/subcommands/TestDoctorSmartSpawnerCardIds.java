package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TestDoctorSmartSpawnerCardIds {

    @Test
    void newMobTypesUseTheSameIdsAsEstablishedCards() {
        assertEquals("IE_MOB_DATA_CARD_ZOMBIE", DoctorSmartSpawnerCommand.expectedCardIdForEntity("ZOMBIE"));
        assertEquals("IE_MOB_DATA_CARD_STRAY", DoctorSmartSpawnerCommand.expectedCardIdForEntity("STRAY"));
        assertEquals("IE_MOB_DATA_CARD_EVOKER", DoctorSmartSpawnerCommand.expectedCardIdForEntity("EVOKER"));
        assertEquals(
                "IE_MOB_DATA_CARD_ELDER_GUARDIAN", DoctorSmartSpawnerCommand.expectedCardIdForEntity("ELDER_GUARDIAN"));
    }

    @Test
    void normalizationPreservesMultiwordMobIds() {
        assertEquals(
                "IE_MOB_DATA_CARD_TROPICAL_FISH", DoctorSmartSpawnerCommand.expectedCardIdForEntity(" tropical fish "));
        assertEquals(
                "IE_MOB_DATA_CARD_WANDERING_TRADER",
                DoctorSmartSpawnerCommand.expectedCardIdForEntity("wandering-trader"));
    }

    @Test
    void vanillaVexKeepsItsOwnIdSeparateFromTheAddonCard() {
        assertEquals("IE_MOB_DATA_CARD_VEX", DoctorSmartSpawnerCommand.expectedCardIdForEntity("VEX"));
        assertEquals(
                "IE_MOB_DATA_CARD_DYNATECH_VEX", DoctorSmartSpawnerCommand.expectedCardIdForEntity("DYNATECH_VEX"));
    }

    @Test
    void missingEntityTypesCannotResolveToACard() {
        assertNull(DoctorSmartSpawnerCommand.expectedCardIdForEntity(null));
        assertNull(DoctorSmartSpawnerCommand.expectedCardIdForEntity(""));
        assertNull(DoctorSmartSpawnerCommand.expectedCardIdForEntity("  "));
        assertNull(DoctorSmartSpawnerCommand.expectedCardIdForEntity("!!!"));
        assertNull(DoctorSmartSpawnerCommand.expectedCardIdForEntity("ITEM"));
    }
}
