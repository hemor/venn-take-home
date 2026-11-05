package com.emmanuel.venn.loads.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.loads.attempts.InMemoryAttemptHistory;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.MDC;
import org.springframework.boot.DefaultApplicationArguments;

class BatchRunnerTest {

    private static final String LINE =
            "{\"id\":\"1\",\"customer_id\":\"10\",\"load_amount\":\"$100.00\",\"time\":\"2018-01-01T00:00:00Z\"}\n";

    @TempDir
    Path dir;

    private final BatchRunner runner = new BatchRunner(BatchFixtures.processor(new InMemoryAttemptHistory()));

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
    void skipsALineWithInvalidUtf8AndProcessesTheRest() throws Exception {
        byte[] first = LINE.getBytes(StandardCharsets.UTF_8);
        byte[] broken = "{\"id\":\"2\",\"customer_id\":\"1\u00e9\",\"load_amount\":\"$1.00\",\"time\":\"2018-01-01T00:00:00Z\"}\n"
                .getBytes(StandardCharsets.ISO_8859_1);
        byte[] last = LINE.replace("\"id\":\"1\"", "\"id\":\"3\"").getBytes(StandardCharsets.UTF_8);
        Path input = dir.resolve("in.txt");
        Files.write(input, concat(first, broken, last));
        Path output = dir.resolve("out.txt");

        runner.run(new DefaultApplicationArguments("--input=" + input, "--output=" + output));

        assertThat(output).hasContent("""
                {"id":"1","customer_id":"10","accepted":true}
                {"id":"3","customer_id":"10","accepted":true}
                """);
    }

    private static byte[] concat(byte[]... parts) {
        return java.util.Arrays.stream(parts).reduce(new byte[0], (a, b) -> {
            byte[] joined = java.util.Arrays.copyOf(a, a.length + b.length);
            System.arraycopy(b, 0, joined, a.length, b.length);
            return joined;
        });
    }

    @Test
    void refusesToOverwriteTheInputFile() throws Exception {
        Path input = Files.writeString(dir.resolve("in.txt"), LINE);

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments(
                "--input=" + input, "--output=" + dir.resolve(".").resolve("in.txt"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same file");
        assertThat(input).hasContent(LINE);
    }

    @Test
    void tagsLogsWithABatchCorrelationIdOnlyWhileRunning() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        BatchRunner capturing = new BatchRunner(new BatchProcessor(null, null) {
            @Override
            BatchSummary process(Reader input, Writer output) {
                seen.set(MDC.get("correlationId"));
                return new BatchSummary(0, 0, 0, 0);
            }
        });
        Path input = Files.writeString(dir.resolve("in.txt"), LINE);

        capturing.run(new DefaultApplicationArguments("--input=" + input, "--output=" + dir.resolve("out.txt")));

        assertThat(seen.get()).startsWith("batch-");
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void detectsBatchModeFromInputOption() {
        assertThat(BatchRunner.isRequested(new String[] {"--input=in.txt", "--output=out.txt"})).isTrue();
        assertThat(BatchRunner.isRequested(new String[] {"--server.port=9000"})).isFalse();
        assertThat(BatchRunner.isRequested(new String[] {"--input-dir=x"})).isFalse();
        assertThat(BatchRunner.isRequested(new String[] {})).isFalse();
    }
}
