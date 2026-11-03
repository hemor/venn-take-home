package com.emmanuel.venn.config;

import com.emmanuel.venn.domain.VelocityLimitPolicy;
import com.emmanuel.venn.domain.VelocityLimits;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VelocityLimitsProperties.class)
class VelocityConfig {

    @Bean
    VelocityLimits velocityLimits(VelocityLimitsProperties properties) {
        return properties.toLimits();
    }

    @Bean
    VelocityLimitPolicy velocityLimitPolicy(VelocityLimits limits) {
        return new VelocityLimitPolicy(limits);
    }
}
