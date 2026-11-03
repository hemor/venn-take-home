package com.emmanuel.venn.service;

import com.emmanuel.venn.domain.Decision;
import com.emmanuel.venn.domain.DuplicateLoadException;
import com.emmanuel.venn.domain.LimitWindows;
import com.emmanuel.venn.domain.LoadAttempt;
import com.emmanuel.venn.domain.LoadAttempts;
import com.emmanuel.venn.domain.PriorActivity;
import java.math.BigDecimal;
import java.time.Instant;
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
    public PriorActivity acceptedActivity(String customerId, LimitWindows windows) {
        List<LoadAttempt> accepted = stored.stream()
                .filter(s -> s.decision().isAccepted() && s.attempt().customerId().equals(customerId))
                .map(Stored::attempt)
                .toList();
        return new PriorActivity(
                total(accepted, windows.dayStart(), windows.dayEnd()),
                total(accepted, windows.weekStart(), windows.weekEnd()),
                (int) accepted.stream().filter(a -> within(a, windows.dayStart(), windows.dayEnd())).count());
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

    private static boolean within(LoadAttempt a, Instant from, Instant to) {
        return !a.time().isBefore(from) && a.time().isBefore(to);
    }

    private static BigDecimal total(List<LoadAttempt> attempts, Instant from, Instant to) {
        return attempts.stream().filter(a -> within(a, from, to)).map(LoadAttempt::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
