package com.emmanuel.venn.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.domain.VelocityLimits;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {"velocity.limits.daily-amount=100", "velocity.limits.weekly-amount=250",
        "velocity.limits.daily-count=1"})
class VelocityLimitsOverrideConfigTest {

    @Autowired
    private VelocityLimits limits;

    @Test
    void limitsCanBeOverriddenThroughConfiguration() {
        assertThat(limits).isEqualTo(new VelocityLimits(new BigDecimal("100"), new BigDecimal("250"), 1));
    }
}
