package com.emmanuel.venn.velocity;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VelocityProperties.class)
class VelocityConfig {

    @Bean
    VelocityLimitPolicy velocityLimitPolicy(VelocityProperties properties) {
        return new VelocityLimitPolicy(properties.toRules());
    }
}
