package com.emmanuel.venn.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.service.InMemoryLoadAttempts;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class BatchRunnerTest {

    private static final String LINE =
            "{\"id\":\"1\",\"customer_id\":\"10\",\"load_amount\":\"$100.00\",\"time\":\"2018-01-01T00:00:00Z\"}\n";

    @TempDir
    Path dir;

    private final BatchRunner runner = new BatchRunner(BatchFixtures.processor(new InMemoryLoadAttempts()));

    @Test
    void writesResponsesToTheOutputFile() throws Exception {
        Path input = Files.writeString(dir.resolve("in.txt"), LINE);
        Path output = dir.resolve("out.txt");

        runner.run(new DefaultApplicationArguments("--input=" + input, "--output=" + output));

        assertThat(output).hasContent("{\"id\":\"1\",\"customer_id\":\"10\",\"accepted\":true}\n");
    }

    @Test
    void doesNothingWithoutInputOption() throws Exception {
        runner.run(new DefaultApplicationArguments());

        assertThat(dir).isEmptyDirectory();
    }

    @Test
    void requiresOutputWhenInputIsGiven() throws Exception {
        Path input = Files.writeString(dir.resolve("in.txt"), LINE);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments("--input=" + input)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("--output");
    }

    @Test
    void failsClearlyWhenInputFileIsMissing() {
        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(
                "--input=" + dir.resolve("missing.txt"), "--output=" + dir.resolve("out.txt"))))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void detectsBatchModeFromInputOption() {
        assertThat(BatchRunner.isRequested(new String[] {"--input=in.txt", "--output=out.txt"})).isTrue();
        assertThat(BatchRunner.isRequested(new String[] {"--server.port=9000"})).isFalse();
        assertThat(BatchRunner.isRequested(new String[] {"--input-dir=x"})).isFalse();
        assertThat(BatchRunner.isRequested(new String[] {})).isFalse();
    }
}
