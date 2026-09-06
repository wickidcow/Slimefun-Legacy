package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Creates a durable, lossless snapshot of every secondary row immediately before storage-integrity repair.
 *
 * <p>The file is deliberately simple and dependency-free. Every field value is type-tagged and Base64 encoded so
 * binary inventory payloads and arbitrary strings remain lossless. Repair fails closed if this backup cannot be written.
 */
final class StorageIntegrityRepairBackup {

    private static final String FORMAT_HEADER = "# Slimefun Legacy storage repair backup v1";

    private StorageIntegrityRepairBackup() {}

    static @Nonnull BackupSnapshot create(
            @Nonnull BlockDataController controller,
            @Nonnull StorageIntegrityRepairPlan plan,
            @Nonnull Path backupDirectory)
            throws IOException {
        List<String> blockData = capture(
                controller,
                DataScope.BLOCK_DATA,
                FieldKey.LOCATION,
                plan.getBlockDataOwners(),
                List.of(FieldKey.LOCATION, FieldKey.DATA_KEY, FieldKey.DATA_VALUE));
        List<String> blockInventory = capture(
                controller,
                DataScope.BLOCK_INVENTORY,
                FieldKey.LOCATION,
                plan.getBlockInventoryOwners(),
                List.of(FieldKey.LOCATION, FieldKey.INVENTORY_SLOT, FieldKey.INVENTORY_ITEM));
        List<String> universalData = capture(
                controller,
                DataScope.UNIVERSAL_DATA,
                FieldKey.UNIVERSAL_UUID,
                plan.getUniversalDataOwners(),
                List.of(FieldKey.UNIVERSAL_UUID, FieldKey.DATA_KEY, FieldKey.DATA_VALUE));
        List<String> universalInventory = capture(
                controller,
                DataScope.UNIVERSAL_INVENTORY,
                FieldKey.UNIVERSAL_UUID,
                plan.getUniversalInventoryOwners(),
                List.of(FieldKey.UNIVERSAL_UUID, FieldKey.INVENTORY_SLOT, FieldKey.INVENTORY_ITEM));

        List<String> lines = new ArrayList<>(2
                + blockData.size()
                + blockInventory.size()
                + universalData.size()
                + universalInventory.size());
        lines.add(FORMAT_HEADER);
        lines.add("fingerprint=" + plan.getFingerprint());
        lines.add("createdAtMillis=" + System.currentTimeMillis());
        lines.addAll(blockData);
        lines.addAll(blockInventory);
        lines.addAll(universalData);
        lines.addAll(universalInventory);

        Files.createDirectories(backupDirectory);
        Path backup = Files.createTempFile(
                backupDirectory, "repair-" + plan.getShortFingerprint() + "-", ".sfbak");
        Files.write(
                backup,
                lines,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);

        return new BackupSnapshot(
                backup,
                blockData.size(),
                blockInventory.size(),
                universalData.size(),
                universalInventory.size());
    }

    private static List<String> capture(
            BlockDataController controller,
            DataScope scope,
            FieldKey ownerField,
            List<String> owners,
            List<FieldKey> fields) {
        List<String> lines = new ArrayList<>();
        for (String owner : owners) {
            RecordKey key = new RecordKey(scope);
            key.addCondition(ownerField, owner);
            fields.forEach(key::addField);
            for (RecordSet row : controller.getData(key)) {
                lines.add(serialize(scope, row.getAllValues()));
            }
        }
        lines.sort(Comparator.naturalOrder());
        return lines;
    }

    static String serialize(DataScope scope, Map<FieldKey, Object> values) {
        List<FieldKey> fields = new ArrayList<>(values.keySet());
        fields.sort(Comparator.comparing(Enum::name));

        StringBuilder line = new StringBuilder(scope.name());
        for (FieldKey field : fields) {
            line.append('\t').append(field.name()).append('=');
            Object value = values.get(field);
            if (value == null) {
                line.append("N:");
            } else if (value instanceof byte[] bytes) {
                line.append("B:").append(Base64.getEncoder().encodeToString(bytes));
            } else {
                byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
                line.append("S:").append(Base64.getEncoder().encodeToString(bytes));
            }
        }
        return line.toString();
    }

    record BackupSnapshot(
            Path path,
            int blockDataRows,
            int blockInventoryRows,
            int universalDataRows,
            int universalInventoryRows) {

        int totalRows() {
            return blockDataRows + blockInventoryRows + universalDataRows + universalInventoryRows;
        }
    }
}
