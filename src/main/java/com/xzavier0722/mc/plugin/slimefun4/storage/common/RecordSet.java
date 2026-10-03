package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.ToString;
import org.bukkit.inventory.ItemStack;

@ToString
public class RecordSet {
    private final Map<FieldKey, Object> data;
    private boolean readonly = false;

    public RecordSet() {
        data = new HashMap<>();
    }

    @ParametersAreNonnullByDefault
    public void put(FieldKey key, String val) {
        putValue(key, val);
    }

    @ParametersAreNonnullByDefault
    public void put(FieldKey key, byte[] val) {
        putValue(key, val.clone());
    }

    @ParametersAreNonnullByDefault
    public void put(FieldKey key, ItemStack itemStack) {
        // Reject writes before invoking an addon-supplied ItemStack serializer.
        checkReadonly();
        putValue(key, DataUtils.serializeItemStackBytesForStorage(itemStack));
    }

    public void put(FieldKey key, boolean val) {
        put(key, val ? "1" : "0");
    }

    private void putValue(FieldKey key, Object value) {
        checkReadonly();
        data.put(key, value);
    }

    /**
     * Returns a legacy String view of this record.
     *
     * <p>Binary fields are represented as Base64 so existing addons that consume this method remain
     * compatible. New storage code should use {@link #getAllValues()}.
     *
     * @return immutable String view
     * @deprecated use {@link #getAllValues()}
     */
    @Deprecated
    @ParametersAreNonnullByDefault
    public Map<FieldKey, String> getAll() {
        var values = new HashMap<FieldKey, String>();
        data.forEach((key, value) -> values.put(key, valueAsString(value)));
        return Collections.unmodifiableMap(values);
    }

    /**
     * Returns a structurally read-only live view with detached binary values.
     *
     * <p>Supported puts remain visible until {@link #readonly()} is called. Each view owns the
     * binary values it exports: editing one can affect that view, never the stored record or a
     * newly requested view. A binary value retains its identity within a view until replaced by
     * a supported put, preserving normal array-valued map equality and hashing behavior.
     * Keys and immutable text can be inspected without copying every binary payload.
     */
    @ParametersAreNonnullByDefault
    public Map<FieldKey, Object> getAllValues() {
        return Collections.unmodifiableMap(new BinaryValueView(data));
    }

    /**
     * Returns a legacy String view of a field.
     *
     * @param key field key
     * @return String value or Base64 for binary fields
     * @deprecated use {@link #getValue(FieldKey)}
     */
    @Deprecated
    @Nullable @ParametersAreNonnullByDefault
    public String get(FieldKey key) {
        return getString(key);
    }

    @Nullable @ParametersAreNonnullByDefault
    public String getString(FieldKey key) {
        return valueAsString(data.get(key));
    }

    /** Returns an immutable scalar or a detached copy of the stored binary value. */
    @Nullable @ParametersAreNonnullByDefault
    public Object getValue(FieldKey key) {
        return copyValue(data.get(key));
    }

    @ParametersAreNonnullByDefault
    public String getOrDef(FieldKey key, String def) {
        var value = getString(key);
        return value == null ? def : value;
    }

    @ParametersAreNonnullByDefault
    public int getInt(FieldKey key) {
        return Integer.parseInt(requireString(key));
    }

    @ParametersAreNonnullByDefault
    public ItemStack getItemStack(FieldKey key) {
        var value = data.get(key);
        if (value instanceof byte[] bytes) {
            return DataUtils.deserializeItemStack(bytes);
        }
        return DataUtils.deserializeStoredItemStack((String) value);
    }

    @ParametersAreNonnullByDefault
    public UUID getUUID(FieldKey key) {
        return UUID.fromString(requireString(key));
    }

    public boolean getBoolean(FieldKey key) {
        return getInt(key) == 1;
    }

    public void readonly() {
        readonly = true;
    }

    private String requireString(FieldKey key) {
        var value = getString(key);
        if (value == null) {
            throw new IllegalStateException("Missing required field: " + key);
        }
        return value;
    }

    @Nullable private static Object copyValue(@Nullable Object value) {
        return value instanceof byte[] bytes ? bytes.clone() : value;
    }

    /** Copies only exported values, once per source buffer and view, not during key traversal. */
    private static final class BinaryValueView extends AbstractMap<FieldKey, Object> {
        private final Map<FieldKey, Object> source;
        private Map<FieldKey, BinaryExport> binaries;

        private BinaryValueView(Map<FieldKey, Object> source) {
            this.source = source;
        }

        // Protect lazy exports when a safely published, frozen record view is read by several callers.
        // This does not make concurrent puts or edits to an exported array thread-safe.
        private synchronized Object exportValue(Object key, Object value) {
            if (value instanceof byte[] bytes) {
                if (binaries == null) {
                    binaries = new HashMap<>();
                }
                var exported = binaries.get(key);
                if (exported == null || exported.source() != bytes) {
                    exported = new BinaryExport(bytes, bytes.clone());
                    binaries.put((FieldKey) key, exported);
                }
                return exported.value();
            }
            if (binaries != null) {
                binaries.remove(key);
            }
            return value;
        }

        @Override
        public Object get(Object key) {
            return exportValue(key, source.get(key));
        }

        @Override
        public boolean containsKey(Object key) {
            return source.containsKey(key);
        }

        @Override
        public int size() {
            return source.size();
        }

        @Override
        public Set<FieldKey> keySet() {
            return Collections.unmodifiableSet(source.keySet());
        }

        @Override
        public Set<Entry<FieldKey, Object>> entrySet() {
            return new AbstractSet<>() {
                @Override
                public int size() {
                    return source.size();
                }

                @Override
                public Iterator<Entry<FieldKey, Object>> iterator() {
                    var entries = source.entrySet().iterator();
                    return new Iterator<>() {
                        @Override
                        public boolean hasNext() {
                            return entries.hasNext();
                        }

                        @Override
                        public Entry<FieldKey, Object> next() {
                            var entry = entries.next();
                            return new SimpleImmutableEntry<>(
                                    entry.getKey(), exportValue(entry.getKey(), entry.getValue()));
                        }
                    };
                }
            };
        }
    }

    private record BinaryExport(byte[] source, byte[] value) {}

    @Nullable private static String valueAsString(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof String string) {
            return string;
        }
        return String.valueOf(value);
    }

    private void checkReadonly() {
        if (readonly) {
            throw new IllegalStateException("RecordSet cannot be modified after readonly() was called.");
        }
    }
}
