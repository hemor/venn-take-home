package com.emmanuel.venn.loads.batch;

import com.emmanuel.venn.loads.LoadHandler;
import com.emmanuel.venn.loads.LoadService;
import com.emmanuel.venn.loads.attempts.AttemptHistory;
import com.emmanuel.venn.velocity.TaskRules;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.Validation;
import tools.jackson.databind.json.JsonMapper;

final class BatchFixtures {

    private BatchFixtures() {}

    static BatchProcessor processor(AttemptHistory attempts) {
        LoadHandler handler = new LoadHandler(
                new LoadService(attempts,
                        TaskRules.policy(),
                    new SimpleMeterRegistry()),
                Validation.buildDefaultValidatorFactory().getValidator());
        return new BatchProcessor(handler, JsonMapper.builder().build());
    }
}
