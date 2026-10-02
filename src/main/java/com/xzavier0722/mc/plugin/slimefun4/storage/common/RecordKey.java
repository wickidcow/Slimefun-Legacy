package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import io.github.bakedlibs.dough.collections.Pair;
import java.util.AbstractSequentialList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

public class RecordKey extends ScopeKey {
    private final Set<FieldKey> fields;
    private final List<Pair<FieldKey, String>> conditions;
    private final List<Pair<FieldKey, String>> conditionView;
    private volatile String strKey = "";
    private volatile boolean changed = true;
    private boolean unique = false;

    @ParametersAreNonnullByDefault
    public RecordKey(DataScope scope) {
        this(scope, Collections.emptySet());
    }

    @ParametersAreNonnullByDefault
    public RecordKey(DataScope scope, Set<FieldKey> fields) {
        this(scope, fields, Collections.emptyList());
    }

    @ParametersAreNonnullByDefault
    public RecordKey(DataScope scope, Set<FieldKey> fields, List<Pair<FieldKey, String>> conditions) {
        super(scope);
        // Own both collections even when a caller supplies an empty or immutable input.
        this.fields = fields.isEmpty() ? new HashSet<>() : new HashSet<>(fields);
        this.conditions = new LinkedList<>();
        conditions.forEach(condition -> this.conditions.add(copyCondition(condition)));
        this.conditionView = Collections.unmodifiableList(new ConditionView(this.conditions));
    }

    @ParametersAreNonnullByDefault
    public void addField(FieldKey field) {
        fields.add(field);
        changed = true;
    }

    @Nonnull
    public Set<FieldKey> getFields() {
        return Collections.unmodifiableSet(fields);
    }

    @ParametersAreNonnullByDefault
    public void addCondition(FieldKey key, String val) {
        conditions.add(new Pair<>(key, val));
        changed = true;
    }

    @ParametersAreNonnullByDefault
    public void addCondition(FieldKey key, boolean val) {
        addCondition(key, val ? "1" : "0");
    }

    @Nonnull
    public List<Pair<FieldKey, String>> getConditions() {
        return conditionView;
    }

    private static Pair<FieldKey, String> copyCondition(Pair<FieldKey, String> condition) {
        return condition == null ? null : new Pair<>(condition.getFirstValue(), condition.getSecondValue());
    }

    /**
     * Retains the historical live, structurally read-only list while detaching mutable pairs.
     * Iteration delegates to the linked list, avoiding repeated indexed traversal.
     */
    private static final class ConditionView extends AbstractSequentialList<Pair<FieldKey, String>> {
        private final List<Pair<FieldKey, String>> source;

        private ConditionView(List<Pair<FieldKey, String>> source) {
            this.source = source;
        }

        @Override
        public int size() {
            return source.size();
        }

        @Override
        public List<Pair<FieldKey, String>> subList(int fromIndex, int toIndex) {
            return new ConditionView(source.subList(fromIndex, toIndex));
        }

        @Override
        public ListIterator<Pair<FieldKey, String>> listIterator(int index) {
            var iterator = source.listIterator(index);
            return new ListIterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Pair<FieldKey, String> next() {
                    return copyCondition(iterator.next());
                }

                @Override
                public boolean hasPrevious() {
                    return iterator.hasPrevious();
                }

                @Override
                public Pair<FieldKey, String> previous() {
                    return copyCondition(iterator.previous());
                }

                @Override
                public int nextIndex() {
                    return iterator.nextIndex();
                }

                @Override
                public int previousIndex() {
                    return iterator.previousIndex();
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void set(Pair<FieldKey, String> value) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void add(Pair<FieldKey, String> value) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    @Override
    protected String getKeyStr() {
        if (changed) {
            var re = new StringBuilder();
            re.append(scope).append("/");
            conditions.forEach(c -> re.append(c.getFirstValue())
                    .append("=")
                    .append(c.getSecondValue())
                    .append("/"));
            fields.forEach(f -> re.append(f).append("/"));
            strKey = re.toString();
            changed = false;
        }

        return strKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof RecordKey other)) {
            return false;
        }

        if (this.scope != other.scope) {
            return false;
        }

        if (this.fields.size() != other.fields.size()) {
            return false;
        }

        var conditionSize = this.conditions.size();
        if (conditionSize != other.conditions.size()) {
            return false;
        }

        for (var field : this.fields) {
            if (!other.fields.contains(field)) {
                return false;
            }
        }

        for (var i = 0; i < conditionSize; i++) {
            if (!this.conditions.get(i).equals(other.conditions.get(i))) {
                return false;
            }
        }

        return true;
    }
}
