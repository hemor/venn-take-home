package com.emmanuel.venn.loads.attempts;

import com.emmanuel.venn.velocity.TimeRange;
import com.emmanuel.venn.velocity.Totals;
import java.util.function.Supplier;

public interface AttemptHistory {

    /**
     * Runs {@code action} while no other caller, on any instance, can run an action for the same customer,
     * and commits everything it saved together.
     */
    <T> T withCustomerLock(String customerId, Supplier<T> action);

    boolean exists(String customerId, String loadId);

    Totals acceptedTotals(String customerId, TimeRange range);

    /** @throws DuplicateLoadException if the customer already has an attempt with this load id */
    void save(LoadAttempt attempt, Decision decision);
}
