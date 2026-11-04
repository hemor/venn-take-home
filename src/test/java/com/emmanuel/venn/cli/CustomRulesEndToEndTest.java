package com.emmanuel.venn.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "velocity.rules[0].name=ONE_LOAD_PER_DAY",
        "velocity.rules[0].window=DAY",
        "velocity.rules[0].metric=COUNT",
        "velocity.rules[0].max=1"})
class CustomRulesEndToEndTest {

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

    @Test
    void configuredRulesReplaceTheDefaultsWithoutCodeChanges() throws Exception {
        Path input = Files.writeString(dir.resolve("in.txt"), """
                {"id":"1","customer_id":"7","load_amount":"$999999.00","time":"2018-01-01T08:00:00Z"}
                {"id":"2","customer_id":"7","load_amount":"$1.00","time":"2018-01-01T09:00:00Z"}
                {"id":"3","customer_id":"7","load_amount":"$1.00","time":"2018-01-02T09:00:00Z"}
                """);
        Path output = dir.resolve("out.txt");

        runner.run(new DefaultApplicationArguments("--input=" + input, "--output=" + output));

        assertThat(output).hasContent("""
                {"id":"1","customer_id":"7","accepted":true}
                {"id":"2","customer_id":"7","accepted":false}
                {"id":"3","customer_id":"7","accepted":true}
                """);
        assertThat(jdbc.queryForObject(
                "select rejection_reason from load_attempt where load_id = '2'", String.class))
                .isEqualTo("ONE_LOAD_PER_DAY");
    }
}
