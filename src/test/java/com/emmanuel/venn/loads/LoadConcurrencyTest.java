package com.emmanuel.venn.loads;

import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.loads.attempts.AttemptHistory;
import com.emmanuel.venn.loads.attempts.Decision;
import com.emmanuel.venn.loads.attempts.LoadAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class LoadConcurrencyTest {

    private static final Instant TIME = Instant.parse("2018-01-01T00:00:00Z");
    private static final int THREADS = 40;

    @Autowired
    private LoadService service;

    @Autowired
    private AttemptHistory history;

    private List<Optional<Decision>> inParallel(int count, java.util.function.IntFunction<LoadAttempt> attempt)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Decision>>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LoadAttempt load = attempt.apply(i);
            futures.add(pool.submit(() -> {
                start.await();
                return service.process(load);
            }));
        }
        start.countDown();
        List<Optional<Decision>> results = new ArrayList<>();
        for (Future<Optional<Decision>> future : futures) {
            results.add(future.get());
        }
        pool.shutdown();
        return results;
    }

    private static long accepted(List<Optional<Decision>> results) {
        return results.stream().filter(r -> r.isPresent() && r.get().isAccepted()).count();
    }

    @Test
    void simultaneousLoadsForOneCustomerNeverExceedTheDailyCount() throws Exception {
        var results = inParallel(THREADS,
                i -> new LoadAttempt("count-" + i, "count-customer", new BigDecimal("1.00"), TIME));

        assertThat(accepted(results)).isEqualTo(3);
    }

    @Test
    void simultaneousLoadsForOneCustomerNeverExceedTheDailyAmount() throws Exception {
        var results = inParallel(THREADS,
                i -> new LoadAttempt("amount-" + i, "amount-customer", new BigDecimal("5000.00"), TIME));

        assertThat(accepted(results)).isEqualTo(1);
    }

    @Test
    void theSameLoadSentSimultaneouslyIsDecidedOnce() throws Exception {
        var results = inParallel(THREADS,
                i -> new LoadAttempt("same", "same-customer", new BigDecimal("10.00"), TIME));

        assertThat(results.stream().filter(Optional::isPresent).count()).isEqualTo(1);
    }

    @Test
    void differentCustomersDoNotAffectEachOther() throws Exception {
        var results = inParallel(THREADS,
                i -> new LoadAttempt("1", "separate-" + i, new BigDecimal("5000.00"), TIME));

        assertThat(accepted(results)).isEqualTo(THREADS);
    }

    @Test
    void customersQueuedBehindAHotCustomerDoNotUseUpTheConnectionPool() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        pool.submit(() -> history.withCustomerLock("hot", () -> {
            holding.countDown();
            await(release);
            return null;
        }));
        holding.await();
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> history.withCustomerLock("hot", () -> null));
        }

        Future<String> other = pool.submit(() -> history.withCustomerLock("other", () -> "done"));

        try {
            assertThat(other.get(1, TimeUnit.SECONDS)).isEqualTo("done");
        } finally {
            release.countDown();
            pool.shutdown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
