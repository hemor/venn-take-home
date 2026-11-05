package com.emmanuel.venn.loads;

import com.emmanuel.venn.loads.attempts.Decision;
import com.emmanuel.venn.loads.attempts.DuplicateLoadException;
import com.emmanuel.venn.loads.attempts.LoadAttempt;
import com.emmanuel.venn.loads.attempts.AttemptHistory;
import com.emmanuel.venn.velocity.VelocityLimitPolicy;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoadService {

    private static final Logger log = LoggerFactory.getLogger(LoadService.class);
    private static final String METRIC = "loads.processed";

    private final AttemptHistory attempts;
    private final VelocityLimitPolicy policy;
    private final MeterRegistry meters;

    public LoadService(AttemptHistory attempts, VelocityLimitPolicy policy, MeterRegistry meters) {
        this.attempts = attempts;
        this.policy = policy;
        this.meters = meters;
    }

    /** @return the decision, or empty when the load id was already seen for this customer */
    public Optional<Decision> process(LoadAttempt attempt) {
        if (attempts.exists(attempt.customerId(), attempt.loadId())) {
            return ignoreDuplicate(attempt);
        }
        Decision decision = policy
                .evaluate(attempt.amount(), attempt.time(),
                        range -> attempts.acceptedTotals(attempt.customerId(), range))
                .map(Decision::rejected)
                .orElseGet(Decision::accepted);
        try {
            attempts.save(attempt, decision);
        } catch (DuplicateLoadException e) {
            return ignoreDuplicate(attempt);
        }
        record(attempt, decision);
        return Optional.of(decision);
    }

    private Optional<Decision> ignoreDuplicate(LoadAttempt attempt) {
        log.info("Ignoring repeated load {} for customer {}", attempt.loadId(), attempt.customerId());
        meters.counter(METRIC, "outcome", "duplicate").increment();
        return Optional.empty();
    }

    private void record(LoadAttempt attempt, Decision decision) {
        if (decision.isAccepted()) {
            log.debug("Accepted load {} for customer {}", attempt.loadId(), attempt.customerId());
            meters.counter(METRIC, "outcome", "accepted").increment();
            return;
        }
        log.info("Rejected load {} for customer {}: {}", attempt.loadId(), attempt.customerId(),
                decision.rejectionReason());
        meters.counter(METRIC, "outcome", "rejected", "reason", decision.rejectionReason()).increment();
    }
}
