package com.emmanuel.venn.loads.attempts;

import com.emmanuel.venn.money.Money;
import com.emmanuel.venn.velocity.TimeRange;
import com.emmanuel.venn.velocity.Totals;
import java.util.Currency;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class InMemoryAttemptHistory implements AttemptHistory {

    public record Stored(LoadAttempt attempt, Decision decision) {}

    public final List<Stored> stored = new ArrayList<>();
    public boolean failNextSaveAsDuplicate;

    @Override
    public <T> T withCustomerLock(String customerId, Supplier<T> action) {
        return action.get();
    }

    @Override
    public boolean exists(String customerId, String loadId) {
        return stored.stream().anyMatch(s -> matches(s, customerId, loadId));
    }

    @Override
    public Totals acceptedTotals(String customerId, TimeRange range, Currency amountCurrency) {
        List<LoadAttempt> accepted = stored.stream()
                .filter(s -> s.decision().isAccepted() && s.attempt().customerId().equals(customerId))
                .map(Stored::attempt)
                .filter(a -> range.contains(a.time()))
                .toList();
        Money sum = accepted.stream().map(LoadAttempt::amount)
                .filter(amount -> amount.currency().equals(amountCurrency))
                .reduce(Money.zero(amountCurrency), Money::plus);
        return new Totals(sum, accepted.size());
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
