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
import org.springframework.transaction.support.TransactionTemplate;

@Repository
class JpaAttemptHistory implements AttemptHistory {

    private final LoadAttemptRepository repository;
    private final CustomerLockRepository locks;
    private final TransactionTemplate transaction;
    private final TransactionTemplate separateTransaction;

    JpaAttemptHistory(LoadAttemptRepository repository, CustomerLockRepository locks,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.locks = locks;
        this.transaction = new TransactionTemplate(transactionManager);
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T withCustomerLock(String customerId, Supplier<T> action) {
        ensureLockRowExists(customerId);
        return transaction.execute(status -> {
            locks.lockByCustomerId(customerId);
            return action.get();
        });
    }

    // Its own transaction: a unique violation from a concurrent first insert would otherwise poison the caller's.
    private void ensureLockRowExists(String customerId) {
        try {
            separateTransaction.executeWithoutResult(status -> {
                if (!locks.existsById(customerId)) {
                    locks.saveAndFlush(new CustomerLockEntity(customerId));
                }
            });
        } catch (DataIntegrityViolationException e) {
            // another request created it first
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
