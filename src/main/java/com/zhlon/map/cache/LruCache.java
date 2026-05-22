package com.zhlon.map.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 通用 LRU 内存缓存。
 *
 * <p>基于 {@link LinkedHashMap} 的 access-order 实现，
 * 以字节数为容量上限，超出时淘汰最久未访问条目。
 *
 * @param <K> 键类型
 * @param <V> 值类型（需实现大小计算）
 */
public class LruCache<K, V extends LruCache.SizedEntry> {

    private final long maxBytes;
    private long currentBytes;
    private final LinkedHashMap<K, V> cache;
    private Consumer<V> evictionListener;

    public LruCache(long maxBytes) {
        this.maxBytes = maxBytes;
        this.currentBytes = 0;
        this.cache = new LinkedHashMap<K, V>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                if (currentBytes > maxBytes) {
                    evictEntry(eldest);
                    return true;
                }
                return false;
            }
        };
    }

    public void setEvictionListener(Consumer<V> listener) {
        this.evictionListener = listener;
    }

    public synchronized V put(K key, V value) {
        V old = cache.remove(key);
        if (old != null) {
            currentBytes -= old.sizeBytes();
        }

        cache.put(key, value);
        currentBytes += value.sizeBytes();

        while (currentBytes > maxBytes && !cache.isEmpty()) {
            var eldest = cache.entrySet().iterator().next();
            evictEntry(eldest);
            cache.remove(eldest.getKey());
        }

        return old;
    }

    public synchronized V get(K key) {
        return cache.get(key);
    }

    public synchronized V remove(K key) {
        V removed = cache.remove(key);
        if (removed != null) {
            currentBytes -= removed.sizeBytes();
        }
        return removed;
    }

    public synchronized void clear() {
        for (V value : cache.values()) {
            if (evictionListener != null) {
                evictionListener.accept(value);
            }
        }
        cache.clear();
        currentBytes = 0;
    }

    public synchronized boolean containsKey(K key) {
        return cache.containsKey(key);
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized long currentBytes() {
        return currentBytes;
    }

    public synchronized long maxBytes() {
        return maxBytes;
    }

    private void evictEntry(Map.Entry<K, V> entry) {
        currentBytes -= entry.getValue().sizeBytes();
        if (evictionListener != null) {
            evictionListener.accept(entry.getValue());
        }
    }

    /** 可计算大小的缓存条目接口 */
    public interface SizedEntry {
        long sizeBytes();
    }
}
