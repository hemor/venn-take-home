package com.emmanuel.venn.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.domain.TaskRules;
import com.emmanuel.venn.domain.VelocityLimitPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DefaultRulesTest {

    @Autowired
    private VelocityLimitPolicy policy;

    @Test
    void applicationYamlDefinesTheThreeLimitsOfTheTask() {
        assertThat(policy.rules()).isEqualTo(TaskRules.rules());
    }
}
