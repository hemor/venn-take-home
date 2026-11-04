package com.emmanuel.venn.domain;

public interface LoadAttempts {

    boolean exists(String customerId, String loadId);

    Totals acceptedTotals(String customerId, TimeRange range);

    /** @throws DuplicateLoadException if the customer already has an attempt with this load id */
    void save(LoadAttempt attempt, Decision decision);
}
