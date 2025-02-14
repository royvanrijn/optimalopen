package com.royvanrijn.optimalopen;

import java.util.*;

/**
 * OptimalOpenHashMap implements Map using open addressing with linear probing.
 *
 * Based on:
 * "Optimal Bounds for Open Addressing Without Reordering"
 * by Martín Farach-Colton∗, Andrew Krapivin†, William Kuszmaul‡ (2025)
 *
 * This version uses lazy deletion via tombstones, cleaning up only when tombstones exceed a threshold.
 */
public class OptimalOpenHashMap<K, V> implements Map<K, V> {

    static final float DEFAULT_LOAD_FACTOR = 0.95f; // allows higher load for better memory utilization
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // 16
    static final int MAXIMUM_CAPACITY = 1 << 30;

    // Unique tombstone marker.
    private static final Entry<?, ?> TOMBSTONE = new Entry<>(null, null, -1);

    private static class Entry<K, V> implements Map.Entry<K, V> {
        final K key;
        V value;
        final int hash; // cached hash value

        Entry(K key, V value, int hash) {
            this.key = key;
            this.value = value;
            this.hash = hash;
        }
        @Override public K getKey() { return key; }
        @Override public V getValue() { return value; }
        @Override public V setValue(V value) {
            V old = this.value;
            this.value = value;
            return old;
        }
        @Override public int hashCode() { return hash ^ Objects.hashCode(value); }
        @Override public boolean equals(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            return hash == ((Entry<?, ?>) e).hash &&
                    Objects.equals(key, e.getKey()) &&
                    Objects.equals(value, e.getValue());
        }
        @Override public String toString() { return key + "=" + value; }
    }

    @SuppressWarnings("unchecked")
    private Entry<K, V>[] table = (Entry<K, V>[]) new Entry[DEFAULT_INITIAL_CAPACITY];
    private int size = 0;
    private int capacity = DEFAULT_INITIAL_CAPACITY;
    private final float loadFactor = DEFAULT_LOAD_FACTOR;
    // Count of tombstones in the table.
    private int tombstones = 0;
    // Increase cleanup threshold to 50% to delay expensive cleanup.
    private static final float TOMBSTONE_THRESHOLD = 0.5f;

    @Override
    public V get(Object key) {
        if (key == null)
            return null;
        int hash = strongHash(key);
        int mask = capacity - 1;
        for (int i = 0; i < capacity; i++) {
            int idx = probeIndex(hash, i, mask);
            Entry<K, V> e = table[idx];
            if (e == null)
                return null;
            if (e != TOMBSTONE && e.hash == hash && Objects.equals(e.key, key))
                return e.value;
        }
        return null;
    }

    @Override
    public V put(K key, V value) {
        if ((size + tombstones + 1.0) / capacity > loadFactor) {
            if (capacity == MAXIMUM_CAPACITY) {
                throw new IllegalStateException("Cannot resize: maximum capacity reached (" + MAXIMUM_CAPACITY + ")");
            }
            resize();
        }
        int hash = strongHash(key);
        int mask = capacity - 1;
        int tombstoneIndex = -1;
        Entry<K, V>[] tab = table;
        for (int i = 0; i < capacity; i++) {
            int idx = probeIndex(hash, i, mask);
            Entry<K, V> e = tab[idx];
            if (e == null) {
                if (tombstoneIndex != -1)
                    idx = tombstoneIndex;
                tab[idx] = new Entry<>(key, value, hash);
                size++;
                return null;
            } else if (e == TOMBSTONE) {
                if (tombstoneIndex == -1)
                    tombstoneIndex = idx;
            } else if (e.hash == hash && Objects.equals(e.key, key)) {
                V oldVal = e.value;
                e.value = value;
                return oldVal;
            }
        }
        throw new IllegalStateException("Table is full");
    }

    @Override
    public V remove(Object key) {
        int hash = strongHash(key);
        int mask = capacity - 1;
        for (int i = 0; i < capacity; i++) {
            int idx = probeIndex(hash, i, mask);
            Entry<K, V> e = table[idx];
            if (e == null)
                return null;
            if (e != TOMBSTONE && e.hash == hash && Objects.equals(e.key, key)) {
                V oldVal = e.value;
                table[idx] = (Entry<K, V>) TOMBSTONE;
                size--;
                tombstones++;
                if (((float) tombstones / capacity) > TOMBSTONE_THRESHOLD) {
                    cleanup();
                }
                return oldVal;
            }
        }
        return null;
    }

    /**
     * Rehashes the entire table to remove tombstones.
     */
    private void cleanup() {
        rehash(capacity);
    }

    @SuppressWarnings("unchecked")
    private void rehash(int newCapacity) {
        int mask = newCapacity - 1;
        Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[newCapacity];
        int newSize = 0;
        for (Entry<K, V> e : table) {
            if (e != null && e != TOMBSTONE) {
                for (int i = 0; i < newCapacity; i++) {
                    int idx = probeIndex(e.hash, i, mask);
                    if (newTable[idx] == null) {
                        newTable[idx] = e;
                        newSize++;
                        break;
                    }
                }
            }
        }
        table = newTable;
        size = newSize;
        tombstones = 0;
        capacity = newCapacity;
    }

    // Linear probing: (baseHash + attempt) & mask, where mask = capacity - 1.
    private int probeIndex(int baseHash, int attempt, int mask) {
        return (baseHash + attempt) & mask;
    }

    // XOR-shift mixing hash function.
    private int strongHash(Object key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return h & 0x7fffffff;
    }

    private void resize() {
        if (capacity >= MAXIMUM_CAPACITY) {
            throw new IllegalStateException("Cannot resize: maximum capacity reached (" + MAXIMUM_CAPACITY + ")");
        }
        int newCapacity = capacity * 2;
        if (newCapacity > MAXIMUM_CAPACITY)
            newCapacity = MAXIMUM_CAPACITY;
        rehash(newCapacity);
    }

    @Override
    public void clear() {
        @SuppressWarnings("unchecked")
        Entry<K, V>[] newTable = (Entry<K, V>[]) new Entry[DEFAULT_INITIAL_CAPACITY];
        table = newTable;
        size = 0;
        capacity = DEFAULT_INITIAL_CAPACITY;
        tombstones = 0;
    }

    @Override public int size() { return size; }
    @Override public boolean isEmpty() { return size == 0; }
    @Override public boolean containsKey(Object key) { return get(key) != null; }
    @Override public boolean containsValue(Object value) {
        for (Entry<K, V> e : table) {
            if (e != null && e != TOMBSTONE && Objects.equals(e.value, value))
                return true;
        }
        return false;
    }
    @Override public void putAll(Map<? extends K, ? extends V> m) {
        for (Map.Entry<? extends K, ? extends V> entry : m.entrySet())
            put(entry.getKey(), entry.getValue());
    }
    @Override public Set<Map.Entry<K, V>> entrySet() { throw new UnsupportedOperationException(); }
    @Override public Set<K> keySet() { throw new UnsupportedOperationException(); }
    @Override public Collection<V> values() { throw new UnsupportedOperationException(); }
}
