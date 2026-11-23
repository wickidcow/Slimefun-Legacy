package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class DoctorNativeNbtEvidenceTest {

    @Test
    void sortsOnlyCompoundKeysButNeverDiscardsTypedInformation() throws IOException {
        byte[] original = synthetic(false, false, false);
        byte[] reversedCompound = synthetic(true, false, false);
        byte[] changedIntegerType = synthetic(true, true, false);

        byte[] preserved = original.clone();
        assertEquals(DoctorNativeNbtEvidence.Verdict.EXACT_NATIVE_BYTES,
                DoctorNativeNbtEvidence.compareNativeBytes(original, original));
        assertEquals(DoctorNativeNbtEvidence.Verdict.SAME_TYPED_NBT,
                DoctorNativeNbtEvidence.compareNativeBytes(original, reversedCompound));
        assertEquals(DoctorNativeNbtEvidence.Verdict.DIFFERENT_TYPED_NBT,
                DoctorNativeNbtEvidence.compareNativeBytes(original, changedIntegerType));
        assertArrayEquals(preserved, original);
    }

    @Test
    void preservesTypedListOrderingAndNeverTreatsItAsCompoundOrdering() throws IOException {
        byte[] original = synthetic(false, false, false);
        byte[] reversedList = synthetic(false, false, true);
        assertEquals(DoctorNativeNbtEvidence.Verdict.DIFFERENT_TYPED_NBT,
                DoctorNativeNbtEvidence.compareNativeBytes(original, reversedList));
    }

    @Test
    void ignoresOnlyRootItemCountAndRetainsNestedTypedCounts() throws IOException {
        byte[] one = synthetic(false, false, false, 1, 33);
        byte[] sixtyFour = synthetic(false, false, false, 64, 33);
        byte[] mutatedNestedCounter = synthetic(false, false, false, 1, 34);

        assertEquals(DoctorNativeNbtEvidence.Verdict.SAME_TYPED_NBT,
                DoctorNativeNbtEvidence.compareNativeBytes(one, sixtyFour));
        assertEquals(DoctorNativeNbtEvidence.Verdict.DIFFERENT_TYPED_NBT,
                DoctorNativeNbtEvidence.compareNativeBytes(one, mutatedNestedCounter));
    }

    @Test
    void refusesConflictingRootQuantityAliasesRatherThanHidingOwnerData() throws IOException {
        byte[] unambiguous = synthetic(false, false, false, 1, 33);
        byte[] ambiguous = synthetic(false, false, false, 1, 33, true);

        assertEquals(DoctorNativeNbtEvidence.Verdict.UNAVAILABLE,
                DoctorNativeNbtEvidence.compareNativeBytes(unambiguous, ambiguous));
        assertEquals(DoctorNativeNbtEvidence.Verdict.UNAVAILABLE,
                DoctorNativeNbtEvidence.compareNativeBytes(ambiguous, unambiguous));
    }

    @Test
    void refusesMalformedOrOverBudgetNativeInput() throws IOException {
        byte[] source = synthetic(false, false, false);
        assertEquals(DoctorNativeNbtEvidence.Verdict.UNAVAILABLE,
                DoctorNativeNbtEvidence.compareNativeBytes(
                        source, Arrays.copyOf(source, source.length - 2)));
        assertEquals(DoctorNativeNbtEvidence.Verdict.UNAVAILABLE,
                DoctorNativeNbtEvidence.compareNativeBytes(source, new byte[0]));
        assertEquals(DoctorNativeNbtEvidence.Verdict.UNAVAILABLE,
                DoctorNativeNbtEvidence.compareNativeBytes(null, source));
        assertEquals(DoctorNativeNbtEvidence.Verdict.UNAVAILABLE,
                DoctorNativeNbtEvidence.compareNativeBytes(source, new byte[256 * 1024 + 1]));
    }

    private static byte[] synthetic(boolean reverseCompound, boolean useLong, boolean reverseList)
            throws IOException {
        return synthetic(reverseCompound, useLong, reverseList, 1, 33);
    }

    private static byte[] synthetic(
            boolean reverseCompound, boolean useLong, boolean reverseList, int rootCount, int nestedCount)
            throws IOException {
        return synthetic(reverseCompound, useLong, reverseList, rootCount, nestedCount, false);
    }

    private static byte[] synthetic(
            boolean reverseCompound,
            boolean useLong,
            boolean reverseList,
            int rootCount,
            int nestedCount,
            boolean conflictingCountAlias) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream zipped = new GZIPOutputStream(bytes);
                DataOutputStream out = new DataOutputStream(zipped)) {
            out.writeByte(10);
            out.writeUTF("");
            if (reverseCompound) {
                writeList(out, reverseList);
            }
            out.writeByte(useLong ? 4 : 3);
            out.writeUTF("typed-value");
            if (useLong) {
                out.writeLong(17L);
            } else {
                out.writeInt(17);
            }
            if (!reverseCompound) {
                writeList(out, reverseList);
            }
            out.writeByte(3);
            out.writeUTF("count");
            out.writeInt(rootCount);
            if (conflictingCountAlias) {
                out.writeByte(3);
                out.writeUTF("Count");
                out.writeInt(rootCount + 1);
            }
            out.writeByte(10);
            out.writeUTF("private-storage");
            out.writeByte(3);
            out.writeUTF("count");
            out.writeInt(nestedCount);
            out.writeByte(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void writeList(DataOutputStream out, boolean reverse) throws IOException {
        out.writeByte(9);
        out.writeUTF("ordered-values");
        out.writeByte(3);
        out.writeInt(2);
        out.writeInt(reverse ? 19 : 11);
        out.writeInt(reverse ? 11 : 19);
    }
}
