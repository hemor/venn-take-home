package com.emmanuel.venn.loads.attempts;

import com.emmanuel.venn.velocity.TimeRange;
import com.emmanuel.venn.velocity.Totals;
import java.math.BigDecimal;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaAttemptHistory implements AttemptHistory {

    private final LoadAttemptRepository repository;

    JpaAttemptHistory(LoadAttemptRepository repository) {
        this.repository = repository;
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
