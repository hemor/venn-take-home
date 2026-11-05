package com.emmanuel.venn.loads.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BatchEndToEndTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");
    private static final Path TASK_INPUT = Path.of("question/Venn - Back-End - Input.txt");
    private static final Path TASK_EXPECTED = Path.of("question/Venn - Back-End - Output .txt");

    @Autowired
    private BatchRunner runner;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path dir;

    @BeforeEach
    void emptyDatabase() {
        jdbc.update("delete from load_attempt");
    }

    private String runBatch(Path input) throws Exception {
        Path output = dir.resolve("output.txt");
        runner.run(new DefaultApplicationArguments("--input=" + input, "--output=" + output));
        return Files.readString(output);
    }

    private static String withoutCarriageReturns(Path file) throws Exception {
        return Files.readString(file).replace("\r", "");
    }

    @Test
    void edgeCaseFixtureProducesTheHandWrittenExpectedOutput() throws Exception {
        String actual = runBatch(FIXTURES.resolve("edge-cases-input.txt"));

        assertThat(actual).isEqualTo(withoutCarriageReturns(FIXTURES.resolve("edge-cases-expected.txt")));
    }

    @Test
    void skipsALineThatRepeatsAKey() throws Exception {
        Path input = Files.writeString(dir.resolve("repeated-key.txt"), """
                {"id":"15","id":"16","customer_id":"7","load_amount":"$1.00","time":"2018-01-01T00:00:00Z"}
                {"id":"17","customer_id":"7","load_amount":"$1.00","time":"2018-01-01T00:00:00Z"}
                """);

        assertThat(runBatch(input)).isEqualTo("{\"id\":\"17\",\"customer_id\":\"7\",\"accepted\":true}\n");
    }

    @Test
    void everyAttemptIsStoredIncludingRejectedOnes() throws Exception {
        runBatch(FIXTURES.resolve("edge-cases-input.txt"));

        Integer accepted = jdbc.queryForObject("select count(*) from load_attempt where status = 'ACCEPTED'", Integer.class);
        Integer rejected = jdbc.queryForObject("select count(*) from load_attempt where status = 'REJECTED'", Integer.class);

        assertThat(accepted).isEqualTo(13);
        assertThat(rejected).isEqualTo(6);
    }

    @Test
    void matchesVennExpectedOutputForTheFullTaskInput() throws Exception {
        assumeTrue(Files.exists(TASK_INPUT) && Files.exists(TASK_EXPECTED),
                "question/ is not committed; drop the task files there to run this comparison");

        String actual = runBatch(TASK_INPUT);

        assertThat(actual).isEqualTo(withoutCarriageReturns(TASK_EXPECTED));
    }
}
