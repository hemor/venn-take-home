package com.emmanuel.venn.loads.attempts;

import com.emmanuel.venn.velocity.TimeRange;
import com.emmanuel.venn.velocity.Totals;

public interface AttemptHistory {

    boolean exists(String customerId, String loadId);

    Totals acceptedTotals(String customerId, TimeRange range);

    /** @throws DuplicateLoadException if the customer already has an attempt with this load id */
    void save(LoadAttempt attempt, Decision decision);
}
