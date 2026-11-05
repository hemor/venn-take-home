package com.emmanuel.venn.loads.attempts;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** One fair mutex per key, forgotten when nobody holds or waits for it. */
class KeyedLock {

    private static final class Entry {
        final ReentrantLock lock = new ReentrantLock(true);
        int users;
    }

    private final ConcurrentHashMap<String, Entry> locks = new ConcurrentHashMap<>();

    <T> T withLock(String key, Supplier<T> action) {
        Entry entry = locks.compute(key, (k, existing) -> {
            Entry e = existing == null ? new Entry() : existing;
            e.users++;
            return e;
        });
        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            locks.compute(key, (k, e) -> --e.users == 0 ? null : e);
        }
    }
}
