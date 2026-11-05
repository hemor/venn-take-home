package com.emmanuel.venn.loads.attempts;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoadAttemptRepository extends JpaRepository<LoadAttemptEntity, Long> {

    boolean existsByCustomerIdAndLoadId(String customerId, String loadId);

    @Query("""
            select sum(a.amount) as total, count(a) as loads
            from LoadAttemptEntity a
            where a.customerId = :customerId
              and a.status = :status
              and a.occurredAt >= :from and a.occurredAt < :to
            """)
    AcceptedTotals totalsWithStatus(@Param("customerId") String customerId, @Param("status") AttemptStatus status,
            @Param("from") Instant from, @Param("to") Instant to);
}
