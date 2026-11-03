package com.emmanuel.venn.persistence;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoadAttemptRepository extends JpaRepository<LoadAttemptEntity, Long> {

    boolean existsByCustomerIdAndLoadId(String customerId, String loadId);

    @Query("""
            select new com.emmanuel.venn.persistence.AcceptedTotals(sum(a.amount), count(a))
            from LoadAttemptEntity a
            where a.customerId = :customerId
              and a.status = com.emmanuel.venn.persistence.AttemptStatus.ACCEPTED
              and a.occurredAt >= :from and a.occurredAt < :to
            """)
    AcceptedTotals acceptedTotals(@Param("customerId") String customerId,
            @Param("from") Instant from, @Param("to") Instant to);
}
