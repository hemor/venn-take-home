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
import java.util.concurrent.CyclicBarrier;
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

/**
 * Several application contexts sharing one H2 server stand in for separate instances. Each round sends exactly one
 * request per instance at the same instant, so each instance's in-process queue is idle and only the row lock
 * can keep the decisions apart.
 */
class MultiInstanceTest {

    private static final Instant TIME = Instant.parse("2018-01-01T00:00:00Z");
    private static final int INSTANCES = 5;
    private static final int ROUNDS = 25;

    private Server database;
    private final List<ConfigurableApplicationContext> instances = new ArrayList<>();

    @BeforeEach
    void startDatabaseAndInstances() throws Exception {
        database = Server.createTcpServer("-tcp", "-tcpPort", "0", "-ifNotExists").start();
        String url = "jdbc:h2:tcp://localhost:" + database.getPort() + "/mem:shared;DB_CLOSE_DELAY=-1";
        // One after another: parallel startup inside one JVM races on logging initialisation.
        for (int i = 0; i < INSTANCES; i++) {
            instances.add(new SpringApplicationBuilder(VennTakeHomeApplication.class)
                    .web(WebApplicationType.NONE)
                    .run("--spring.datasource.url=" + url, "--spring.jmx.enabled=false"));
        }
    }

    @AfterEach
    void stop() {
        instances.forEach(ConfigurableApplicationContext::close);
        if (database != null) {
            database.stop();
        }
    }

    private List<Optional<Decision>> oneLoadPerInstanceAtOnce(String customerId, String amount) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);
        CyclicBarrier together = new CyclicBarrier(INSTANCES);
        List<Future<Optional<Decision>>> futures = new ArrayList<>();
        for (int i = 0; i < INSTANCES; i++) {
            LoadService instance = instances.get(i).getBean(LoadService.class);
            LoadAttempt load = new LoadAttempt("load-" + i, customerId, new BigDecimal(amount), TIME);
            futures.add(pool.submit(() -> {
                together.await();
                return instance.process(load);
            }));
        }
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

    private static long rejected(List<Optional<Decision>> results) {
        return results.stream().filter(r -> r.isPresent() && !r.get().isAccepted()).count();
    }

    @Test
    void fiveInstancesAcceptExactlyThreeLoadsAndRejectTwoForOneCustomer() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var results = oneLoadPerInstanceAtOnce("count-" + round, "1.00");

            assertThat(accepted(results)).as("accepted in round %d", round).isEqualTo(3);
            assertThat(rejected(results)).as("rejected in round %d", round).isEqualTo(2);
        }
    }

    @Test
    void fiveInstancesAcceptExactlyOneLoadOverTheDailyAmount() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            var results = oneLoadPerInstanceAtOnce("amount-" + round, "5000.00");

            assertThat(accepted(results)).as("accepted in round %d", round).isEqualTo(1);
            assertThat(rejected(results)).as("rejected in round %d", round).isEqualTo(4);
        }
    }
}
