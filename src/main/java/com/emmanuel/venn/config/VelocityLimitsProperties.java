package com.emmanuel.venn.config;

import com.emmanuel.venn.domain.VelocityLimits;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("velocity.limits")
record VelocityLimitsProperties(BigDecimal dailyAmount, BigDecimal weeklyAmount, int dailyCount) {

    VelocityLimits toLimits() {
        return new VelocityLimits(dailyAmount, weeklyAmount, dailyCount);
    }
}
