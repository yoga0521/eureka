package com.netflix.discovery.util;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * An alternative to {@link String#intern()} with no capacity constraints.
 *
 * @author Tomasz Bak
 */
public class StringCache {

    public static final int LENGTH_LIMIT = 38;

    private static final StringCache INSTANCE = new StringCache();

    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<String, WeakReference<String>> cache = new WeakHashMap<String, WeakReference<String>>();
    private final int lengthLimit;

    public StringCache() {
        this(LENGTH_LIMIT);
    }

    public StringCache(int lengthLimit) {
        this.lengthLimit = lengthLimit;
    }

    public String cachedValueOf(final String str) {
        if (str != null && (lengthLimit < 0 || str.length() <= lengthLimit)) {
            // Return value from cache if available
            try {
                // 加读锁
                lock.readLock().lock();
                // 从缓存中获取
                WeakReference<String> ref = cache.get(str);
                // 如果存在就返回
                if (ref != null) {
                    return ref.get();
                }
            } finally {
                // 解除读锁
                lock.readLock().unlock();
            }

            // Update cache with new content
            try {
                // 加写锁
                lock.writeLock().lock();
                // 从缓存中获取
                WeakReference<String> ref = cache.get(str);
                // 如果存在就反悔啊
                if (ref != null) {
                    return ref.get();
                }
                // 没有就添加
                cache.put(str, new WeakReference<>(str));
            } finally {
                // 解除写锁
                lock.writeLock().unlock();
            }
            return str;
        }
        return str;
    }

    public int size() {
        try {
            lock.readLock().lock();
            return cache.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public static String intern(String original) {
        return INSTANCE.cachedValueOf(original);
    }
}
