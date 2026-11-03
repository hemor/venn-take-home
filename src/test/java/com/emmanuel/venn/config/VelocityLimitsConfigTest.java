package com.emmanuel.venn.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.emmanuel.venn.domain.VelocityLimits;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class VelocityLimitsConfigTest {

    @Autowired
    private VelocityLimits limits;

    @Test
    void bindsDefaultLimitsFromApplicationYaml() {
        assertThat(limits.dailyAmount()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(limits.weeklyAmount()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(limits.dailyCount()).isEqualTo(3);
    }
}
