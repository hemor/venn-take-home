package com.emmanuel.venn.persistence;

import com.emmanuel.venn.domain.Decision;
import com.emmanuel.venn.domain.DuplicateLoadException;
import com.emmanuel.venn.domain.LimitWindows;
import com.emmanuel.venn.domain.LoadAttempt;
import com.emmanuel.venn.domain.LoadAttempts;
import com.emmanuel.venn.domain.PriorActivity;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
class JpaLoadAttempts implements LoadAttempts {

    private final LoadAttemptRepository repository;

    JpaLoadAttempts(LoadAttemptRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean exists(String customerId, String loadId) {
        return repository.existsByCustomerIdAndLoadId(customerId, loadId);
    }

    @Override
    public PriorActivity acceptedActivity(String customerId, LimitWindows windows) {
        AcceptedTotals day = repository.acceptedTotals(customerId, windows.dayStart(), windows.dayEnd());
        AcceptedTotals week = repository.acceptedTotals(customerId, windows.weekStart(), windows.weekEnd());
        return new PriorActivity(day.total(), week.total(), Math.toIntExact(day.count()));
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
