package com.emmanuel.venn.loads.attempts;

import com.emmanuel.venn.money.Money;
import java.time.Instant;

public record LoadAttempt(String loadId, String customerId, Money amount, Instant time) {}
