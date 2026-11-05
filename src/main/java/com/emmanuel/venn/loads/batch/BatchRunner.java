package com.emmanuel.venn.loads.batch;

import com.emmanuel.venn.observability.CorrelationIdFilter;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs a batch when started with {@code --input=<file> --output=<file>}; otherwise does nothing. */
@Component
public class BatchRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BatchRunner.class);
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";

    private final BatchProcessor processor;

    BatchRunner(BatchProcessor processor) {
        this.processor = processor;
    }

    public static boolean isRequested(String[] args) {
        return Arrays.stream(args).anyMatch(arg -> arg.equals("--input") || arg.startsWith("--input="));
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!args.containsOption(INPUT)) {
            return;
        }
        Path input = Path.of(requiredOption(args, INPUT));
        Path output = Path.of(requiredOption(args, OUTPUT));
        if (Files.exists(output) && Files.isSameFile(input, output)) {
            throw new IllegalArgumentException("--input and --output are the same file: " + input);
        }
        try (MDC.MDCCloseable ignored = MDC.putCloseable(CorrelationIdFilter.MDC_KEY, "batch-" + UUID.randomUUID());
                BufferedReader reader = Files.newBufferedReader(input);
                BufferedWriter writer = Files.newBufferedWriter(output)) {
            log.info("Processing {} into {}", input, output);
            try {
                processor.process(reader, writer);
            } catch (RuntimeException e) {
                log.error("Aborting batch, the output file is incomplete", e);
                throw e;
            }
        }
    }

    private static String requiredOption(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty() || values.get(0).isBlank()) {
            throw new IllegalArgumentException("--" + name + "=<file> is required in batch mode");
        }
        return values.get(0);
    }
}
