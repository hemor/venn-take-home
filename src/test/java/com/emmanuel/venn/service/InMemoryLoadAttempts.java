package com.emmanuel.venn.service;

import com.emmanuel.venn.domain.Decision;
import com.emmanuel.venn.domain.DuplicateLoadException;
import com.emmanuel.venn.domain.LoadAttempt;
import com.emmanuel.venn.domain.LoadAttempts;
import com.emmanuel.venn.domain.TimeRange;
import com.emmanuel.venn.domain.Totals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class InMemoryLoadAttempts implements LoadAttempts {

    public record Stored(LoadAttempt attempt, Decision decision) {}

    public final List<Stored> stored = new ArrayList<>();
    public boolean failNextSaveAsDuplicate;

    @Override
    public boolean exists(String customerId, String loadId) {
        return stored.stream().anyMatch(s -> matches(s, customerId, loadId));
    }

    @Override
    public Totals acceptedTotals(String customerId, TimeRange range) {
        List<LoadAttempt> accepted = stored.stream()
                .filter(s -> s.decision().isAccepted() && s.attempt().customerId().equals(customerId))
                .map(Stored::attempt)
                .filter(a -> range.contains(a.time()))
                .toList();
        return new Totals(
                accepted.stream().map(LoadAttempt::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                accepted.size());
    }

    @Override
    public void save(LoadAttempt attempt, Decision decision) {
        if (failNextSaveAsDuplicate || exists(attempt.customerId(), attempt.loadId())) {
            failNextSaveAsDuplicate = false;
            throw new DuplicateLoadException(attempt.customerId(), attempt.loadId(), null);
        }
        stored.add(new Stored(attempt, decision));
    }

    private static boolean matches(Stored s, String customerId, String loadId) {
        return s.attempt().customerId().equals(customerId) && s.attempt().loadId().equals(loadId);
    }
}
