package com.emmanuel.venn.loads.attempts;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class KeyedLockTest {

    private final KeyedLock lock = new KeyedLock();

    @Test
    void neverRunsTwoActionsForTheSameKeyAtOnce() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger mostAtOnce = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);

        Future<?>[] futures = new Future<?>[200];
        for (int i = 0; i < futures.length; i++) {
            futures[i] = pool.submit(() -> lock.withLock("k", () -> {
                mostAtOnce.accumulateAndGet(running.incrementAndGet(), Math::max);
                Thread.onSpinWait();
                running.decrementAndGet();
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(mostAtOnce).hasValue(1);
    }

    @Test
    void runsActionsForDifferentKeysAtTheSameTime() throws Exception {
        CountDownLatch bothInside = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Future<Boolean> a = pool.submit(() -> lock.withLock("a", () -> arriveAndWait(bothInside)));
        Future<Boolean> b = pool.submit(() -> lock.withLock("b", () -> arriveAndWait(bothInside)));

        assertThat(a.get(5, TimeUnit.SECONDS)).isTrue();
        assertThat(b.get(5, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();
    }

    @Test
    void releasesTheKeyWhenTheActionThrows() {
        try {
            lock.withLock("k", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the lock must still be free
        }

        assertThat(lock.withLock("k", () -> "ok")).isEqualTo("ok");
    }

    @Test
    void forgetsKeysOnceNobodyHoldsOrWaitsForThem() throws Exception {
        for (int i = 0; i < 100; i++) {
            lock.withLock("customer-" + i, () -> null);
        }

        Field field = KeyedLock.class.getDeclaredField("locks");
        field.setAccessible(true);
        assertThat((Map<?, ?>) field.get(lock)).isEmpty();
    }

    private static boolean arriveAndWait(CountDownLatch latch) {
        latch.countDown();
        try {
            return latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
