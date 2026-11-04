package com.emmanuel.venn.cli;

import com.emmanuel.venn.api.LoadHandler;
import com.emmanuel.venn.domain.LoadAttempts;
import com.emmanuel.venn.domain.TaskRules;
import com.emmanuel.venn.service.LoadService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import tools.jackson.databind.json.JsonMapper;

final class BatchFixtures {

    private BatchFixtures() {}

    static BatchProcessor processor(LoadAttempts attempts) {
        LoadHandler handler = new LoadHandler(
                new LoadService(attempts,
                        TaskRules.policy(),
                    new SimpleMeterRegistry()),
                Validation.buildDefaultValidatorFactory().getValidator());
        return new BatchProcessor(handler, JsonMapper.builder().build());
    }
}
