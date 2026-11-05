package com.emmanuel.venn.loads;

import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.VennTakeHomeApplication;
import com.emmanuel.venn.loads.attempts.Decision;
import com.emmanuel.venn.loads.attempts.LoadAttempt;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.h2.tools.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** Two application instances sharing one H2 server, each with its own in-process queue and pool. */
class MultiInstanceTest {

    private static final Instant TIME = Instant.parse("2018-01-01T00:00:00Z");
    private static final int REQUESTS_PER_INSTANCE = 30;

    private Server database;
    private ConfigurableApplicationContext first;
    private ConfigurableApplicationContext second;

    @BeforeEach
    void startDatabaseAndTwoInstances() throws Exception {
        database = Server.createTcpServer("-tcp", "-tcpPort", "0", "-ifNotExists").start();
        String url = "jdbc:h2:tcp://localhost:" + database.getPort() + "/mem:shared;DB_CLOSE_DELAY=-1";
        CompletableFuture<ConfigurableApplicationContext> a = CompletableFuture.supplyAsync(() -> startInstance(url));
        CompletableFuture<ConfigurableApplicationContext> b = CompletableFuture.supplyAsync(() -> startInstance(url));
        first = a.get();
        second = b.get();
    }

    @AfterEach
    void stop() {
        for (ConfigurableApplicationContext context : new ConfigurableApplicationContext[] {first, second}) {
            if (context != null) {
                context.close();
            }
        }
        if (database != null) {
            database.stop();
        }
    }

    private static ConfigurableApplicationContext startInstance(String url) {
        return new SpringApplicationBuilder(VennTakeHomeApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url, "--spring.jmx.enabled=false");
    }

    private List<Optional<Decision>> loadsFromBothInstances(String customerId, String amount) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(REQUESTS_PER_INSTANCE * 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Optional<Decision>>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS_PER_INSTANCE * 2; i++) {
            LoadService instance = (i % 2 == 0 ? first : second).getBean(LoadService.class);
            LoadAttempt load = new LoadAttempt("load-" + i, customerId, new BigDecimal(amount), TIME);
            futures.add(pool.submit(() -> {
                start.await();
                return instance.process(load);
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
    void twoInstancesNeverExceedTheDailyCountForOneCustomer() throws Exception {
        assertThat(accepted(loadsFromBothInstances("count-customer", "1.00"))).isEqualTo(3);
    }

    @Test
    void twoInstancesNeverExceedTheDailyAmountForOneCustomer() throws Exception {
        assertThat(accepted(loadsFromBothInstances("amount-customer", "5000.00"))).isEqualTo(1);
    }
}
