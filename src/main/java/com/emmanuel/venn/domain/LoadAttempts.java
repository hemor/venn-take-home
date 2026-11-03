package com.emmanuel.venn.domain;

public interface LoadAttempts {

    boolean exists(String customerId, String loadId);

    PriorActivity acceptedActivity(String customerId, LimitWindows windows);

    /** @throws DuplicateLoadException if the customer already has an attempt with this load id */
    void save(LoadAttempt attempt, Decision decision);
}
