package com.emmanuel.venn.loads.attempts;

import java.math.BigDecimal;
import java.time.Instant;

public record LoadAttempt(String loadId, String customerId, BigDecimal amount, Instant time) {}
