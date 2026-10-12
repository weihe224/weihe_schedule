package com.weihe.schedule.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory {@link SharedPreferences} for tests.
 *
 * <p>{@link CourseRepository} was untestable because it takes a {@link Context}
 * and reads preferences through it. The interfaces themselves are plain Java --
 * only the {@code Context} lookup is Android -- so a stand-in for the store is
 * enough to exercise the repository's real logic: the JSON round trip, the
 * cache, the damaged-payload backup, id assignment, and the schedule rename
 * cascade.</p>
 *
 * <p>Nothing here may touch an Android class other than these two interfaces;
 * the unit tests run on a bare JVM where every other Android method is a stub
 * that throws.</p>
 */
public final class FakePreferences implements SharedPreferences {

    private final Map<String, Object> values = new HashMap<>();

    /**
     * How many times {@link Editor#commit()} has succeeded.
     *
     * <p>Lets a test say "this operation is one transaction" rather than only
     * "it ended up with the right values" -- which is the difference between a
     * restore that cannot be half-applied and one that merely happened not to
     * fail.</p>
     */
    private int commits;

    /**
     * When above zero, the commit with this number reports failure without
     * changing anything.
     *
     * <p>This is what a real store does when the write cannot be persisted: the
     * in-memory map keeps the old values and {@code commit()} returns false.
     * Naming the commit lets a test fail exactly the one it means -- the restore,
     * or the rollback after it -- and leave the other able to succeed.</p>
     */
    private int failingCommitNumber;

    /** Makes the {@code number}-th commit (counting from 1) fail. */
    public void failCommitNumber(int number) {
        failingCommitNumber = number;
    }

    /** How many commits have gone through. */
    public int commitCount() {
        return commits;
    }

    /** Every string currently stored, for assertions. */
    public Map<String, Object> snapshot() {
        return new HashMap<>(values);
    }

    @Override
    public Map<String, ?> getAll() {
        return snapshot();
    }

    @Override
    public String getString(String key, String defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        // The platform casts, so a key holding a number throws here as well. Being
        // forgiving would hide the one case the code under test has to handle: a
        // slot that holds something other than text.
        return (String) value;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Set<String> getStringSet(String key, Set<String> defaultValues) {
        Object value = values.get(key);
        return value instanceof Set ? new HashSet<>((Set<String>) value)
                : defaultValues;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        Object value = values.get(key);
        return value instanceof Integer ? ((Integer) value).intValue() : defaultValue;
    }

    @Override
    public long getLong(String key, long defaultValue) {
        Object value = values.get(key);
        return value instanceof Long ? ((Long) value).longValue() : defaultValue;
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        Object value = values.get(key);
        return value instanceof Float ? ((Float) value).floatValue() : defaultValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = values.get(key);
        return value instanceof Boolean ? ((Boolean) value).booleanValue() : defaultValue;
    }

    @Override
    public boolean contains(String key) {
        return values.containsKey(key);
    }

    @Override
    public Editor edit() {
        return new FakeEditor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
    }

    /**
     * Applies writes straight away, like a real store after {@code commit()}.
     *
     * <p>Readers are written to be tolerant of a plain map of primitives and
     * strings, but the app stores JSON as strings, so this is enough for the
     * cases the repository deals with.</p>
     */
    private final class FakeEditor implements Editor {

        private final Map<String, Object> pending = new HashMap<>();
        private final List<String> removals = new ArrayList<>();
        private boolean clear;

        @Override
        public Editor putString(String key, String value) {
            pending.put(key, value);
            return this;
        }

        @Override
        public Editor putStringSet(String key, Set<String> value) {
            pending.put(key, value == null ? null : new HashSet<>(value));
            return this;
        }

        @Override
        public Editor putInt(String key, int value) {
            pending.put(key, Integer.valueOf(value));
            return this;
        }

        @Override
        public Editor putLong(String key, long value) {
            pending.put(key, Long.valueOf(value));
            return this;
        }

        @Override
        public Editor putFloat(String key, float value) {
            pending.put(key, Float.valueOf(value));
            return this;
        }

        @Override
        public Editor putBoolean(String key, boolean value) {
            pending.put(key, Boolean.valueOf(value));
            return this;
        }

        @Override
        public Editor remove(String key) {
            removals.add(key);
            return this;
        }

        @Override
        public Editor clear() {
            clear = true;
            return this;
        }

        @Override
        public boolean commit() {
            if (failingCommitNumber > 0 && commits + 1 == failingCommitNumber) {
                // A refused commit leaves the store exactly as it was, which is
                // what the real implementation does too.
                pending.clear();
                removals.clear();
                clear = false;
                return false;
            }
            apply();
            commits++;
            return true;
        }

        @Override
        public void apply() {
            if (clear) {
                values.clear();
            }
            for (String key : removals) {
                values.remove(key);
            }
            for (Map.Entry<String, Object> entry : pending.entrySet()) {
                if (entry.getValue() == null) {
                    values.remove(entry.getKey());
                } else {
                    values.put(entry.getKey(), entry.getValue());
                }
            }
            pending.clear();
            removals.clear();
            clear = false;
        }
    }
}
