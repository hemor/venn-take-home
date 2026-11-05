package com.emmanuel.venn.velocity;

import static org.assertj.core.api.Assertions.assertThat;

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
