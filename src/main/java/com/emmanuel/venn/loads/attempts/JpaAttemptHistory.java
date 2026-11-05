package com.emmanuel.venn.loads.attempts;

import com.emmanuel.venn.velocity.TimeRange;
import com.emmanuel.venn.velocity.Totals;
import java.math.BigDecimal;
import java.util.function.Supplier;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JpaAttemptHistory implements AttemptHistory {

    private final LoadAttemptRepository repository;
    private final CustomerLockRepository locks;
    private final KeyedLock customerQueue = new KeyedLock();
    private final TransactionTemplate transaction;

    private record Outcome<T>(boolean locked, T value) {}

    JpaAttemptHistory(LoadAttemptRepository repository, CustomerLockRepository locks,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.locks = locks;
        this.transaction = new TransactionTemplate(transactionManager);
        // The totals must see the previous lock holder's commit, which a snapshot taken before the wait would not.
        this.transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public <T> T withCustomerLock(String customerId, Supplier<T> action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "The customer lock must be taken outside a transaction, or waiting holds a second connection");
        }
        // Waiting here costs a parked thread; waiting on the row lock would hold a pooled connection.
        return customerQueue.withLock(customerId, () -> {
            Outcome<T> outcome = lockAndRun(customerId, action);
            if (!outcome.locked()) {
                createLockRow(customerId);
                outcome = lockAndRun(customerId, action);
            }
            if (!outcome.locked()) {
                throw new IllegalStateException("Lock row for customer " + customerId + " is missing after creating it");
            }
            return outcome.value();
        });
    }

    private <T> Outcome<T> lockAndRun(String customerId, Supplier<T> action) {
        return transaction.execute(status -> locks.lockByCustomerId(customerId)
                .map(row -> new Outcome<>(true, action.get()))
                .orElseGet(() -> new Outcome<>(false, null)));
    }

    // Its own transaction: a unique violation from a concurrent first insert would otherwise poison a caller's.
    private void createLockRow(String customerId) {
        try {
            transaction.executeWithoutResult(status -> locks.saveAndFlush(new CustomerLockEntity(customerId)));
        } catch (DataIntegrityViolationException e) {
            // another instance created it first
        }
    }

    @Override
    public boolean exists(String customerId, String loadId) {
        return repository.existsByCustomerIdAndLoadId(customerId, loadId);
    }

    @Override
    public Totals acceptedTotals(String customerId, TimeRange range) {
        AcceptedTotals accepted = repository.totalsWithStatus(customerId, AttemptStatus.ACCEPTED,
                range.start(), range.end());
        BigDecimal amount = accepted.getTotal() == null ? BigDecimal.ZERO : accepted.getTotal();
        return new Totals(amount, Math.toIntExact(accepted.getLoads()));
    }

    @Override
    public void save(LoadAttempt attempt, Decision decision) {
        AttemptStatus status = decision.isAccepted() ? AttemptStatus.ACCEPTED : AttemptStatus.REJECTED;
        try {
            repository.saveAndFlush(new LoadAttemptEntity(attempt.loadId(), attempt.customerId(),
                    attempt.amount(), attempt.time(), status, decision.rejectionReason()));
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e)) {
                throw new DuplicateLoadException(attempt.customerId(), attempt.loadId(), e);
            }
            throw e;
        }
    }

    private static boolean isUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE;
    }
}
