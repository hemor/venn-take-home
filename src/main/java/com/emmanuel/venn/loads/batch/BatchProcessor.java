package com.emmanuel.venn.loads.batch;

import com.emmanuel.venn.loads.InvalidLoadException;
import com.emmanuel.venn.loads.LoadHandler;
import com.emmanuel.venn.loads.LoadRequest;
import com.emmanuel.venn.loads.LoadResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Processes JSON lines one at a time. Bad lines are skipped, infrastructure failures abort. */
@Component
class BatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(BatchProcessor.class);

    private static final String BYTE_ORDER_MARK = "\uFEFF";

    private final LoadHandler handler;
    private final JsonMapper mapper;

    BatchProcessor(LoadHandler handler, JsonMapper mapper) {
        this.handler = handler;
        this.mapper = mapper;
    }

    BatchSummary process(Reader input, Writer output) throws IOException {
        BufferedReader reader = new BufferedReader(input);
        int lineNumber = 0;
        int linesRead = 0;
        int responses = 0;
        int duplicates = 0;
        int invalid = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (lineNumber == 1 && line.startsWith(BYTE_ORDER_MARK)) {
                line = line.substring(1);
            }
            if (line.isBlank()) {
                continue;
            }
            linesRead++;
            try {
                Optional<LoadResponse> response = handler.handle(parse(line));
                if (response.isPresent()) {
                    output.write(mapper.writeValueAsString(response.get()));
                    output.write('\n');
                    responses++;
                } else {
                    duplicates++;
                }
            } catch (InvalidLoadException | JacksonException e) {
                invalid++;
                log.warn("Skipping line {}: {}", lineNumber, e.getMessage());
            }
        }
        output.flush();
        BatchSummary summary = new BatchSummary(linesRead, responses, duplicates, invalid);
        log.info("Batch finished: {}", summary);
        return summary;
    }

    private LoadRequest parse(String line) {
        LoadRequest request = mapper.readValue(line, LoadRequest.class);
        if (request == null) {
            throw new InvalidLoadException("line must be a JSON object");
        }
        return request;
    }
}
