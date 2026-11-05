package com.emmanuel.venn.loads.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.emmanuel.venn.loads.attempts.Decision;
import com.emmanuel.venn.loads.attempts.InMemoryAttemptHistory;
import com.emmanuel.venn.loads.attempts.LoadAttempt;
import java.io.StringReader;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class BatchProcessorTest {

    private static final String T0 = "2018-01-01T00:00:00Z";

    private final InMemoryAttemptHistory store = new InMemoryAttemptHistory();

    private BatchSummary run(InMemoryAttemptHistory attempts, String input, StringWriter output) throws Exception {
        return BatchFixtures.processor(attempts).process(new StringReader(input), output);
    }

    private static String line(String id, String customerId, String amount, String time) {
        return "{\"id\":\"%s\",\"customer_id\":\"%s\",\"load_amount\":\"%s\",\"time\":\"%s\"}"
                .formatted(id, customerId, amount, time);
    }

    @Test
    void logsAnUnreadableLineWithoutEchoingIt(CapturedOutput output) throws Exception {
        BatchSummary summary = run(store, "SECRETTOKENabc\n", new StringWriter());

        assertThat(summary.invalidLines()).isEqualTo(1);
        assertThat(output).contains("Skipping line 1: unreadable JSON").doesNotContain("SECRETTOKENabc");
    }

    @Test
    void ignoresAByteOrderMarkAtTheStartOfTheInput() throws Exception {
        StringWriter out = new StringWriter();

        BatchSummary summary = run(store, "\uFEFF" + line("1", "10", "$1.00", T0) + "\n", out);

        assertThat(out.toString()).isEqualTo("{\"id\":\"1\",\"customer_id\":\"10\",\"accepted\":true}\n");
        assertThat(summary.invalidLines()).isZero();
    }

    @Test
    void writesOneResponseLinePerDecisionInInputOrder() throws Exception {
        StringWriter out = new StringWriter();
        String input = line("1", "10", "$100.00", T0) + "\n" + line("2", "10", "$6000.00", T0) + "\n";

        BatchSummary summary = run(store, input, out);

        assertThat(out.toString()).isEqualTo("""
                {"id":"1","customer_id":"10","accepted":true}
                {"id":"2","customer_id":"10","accepted":false}
                """);
        assertThat(summary).isEqualTo(new BatchSummary(2, 2, 0, 0));
    }

    @Test
    void repeatedLoadIdProducesNoOutputLine() throws Exception {
        StringWriter out = new StringWriter();
        String input = line("1", "10", "$100.00", T0) + "\n" + line("1", "10", "$100.00", T0) + "\n";

        BatchSummary summary = run(store, input, out);

        assertThat(out.toString().lines()).hasSize(1);
        assertThat(summary).isEqualTo(new BatchSummary(2, 1, 1, 0));
    }

    @Test
    void skipsBlankLinesWithoutCountingThem() throws Exception {
        StringWriter out = new StringWriter();

        BatchSummary summary = run(store, "\n" + line("1", "10", "$1.00", T0) + "\n   \n", out);

        assertThat(summary).isEqualTo(new BatchSummary(1, 1, 0, 0));
    }

    @Test
    void malformedJsonDoesNotStopTheBatch() throws Exception {
        StringWriter out = new StringWriter();
        String input = "{not json\n" + line("1", "10", "$1.00", T0) + "\n";

        BatchSummary summary = run(store, input, out);

        assertThat(out.toString().lines()).hasSize(1);
        assertThat(summary).isEqualTo(new BatchSummary(2, 1, 0, 1));
    }

    @Test
    void jsonThatIsNotAnObjectIsInvalid() throws Exception {
        StringWriter out = new StringWriter();

        BatchSummary summary = run(store, "null\n[]\n\"text\"\n", out);

        assertThat(out.toString()).isEmpty();
        assertThat(summary).isEqualTo(new BatchSummary(3, 0, 0, 3));
    }

    @Test
    void invalidLoadDoesNotStopTheBatchAndIsNotStored() throws Exception {
        StringWriter out = new StringWriter();
        String input = line("1", "10", "oops", T0) + "\n" + line("2", "10", "$1.00", T0) + "\n";

        BatchSummary summary = run(store, input, out);

        assertThat(out.toString().lines()).hasSize(1);
        assertThat(summary).isEqualTo(new BatchSummary(2, 1, 0, 1));
        assertThat(store.stored).hasSize(1);
    }

    @Test
    void infrastructureFailuresAbortTheBatch() {
        InMemoryAttemptHistory brokenStore = new InMemoryAttemptHistory() {
            @Override
            public void save(LoadAttempt attempt, Decision decision) {
                throw new IllegalStateException("database down");
            }
        };

        assertThatThrownBy(() -> run(brokenStore, line("1", "10", "$1.00", T0) + "\n", new StringWriter()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database down");
    }
}
