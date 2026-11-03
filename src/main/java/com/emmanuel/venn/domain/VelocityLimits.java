package com.emmanuel.venn.domain;

import java.math.BigDecimal;

public record VelocityLimits(BigDecimal dailyAmount, BigDecimal weeklyAmount, int dailyCount) {}
