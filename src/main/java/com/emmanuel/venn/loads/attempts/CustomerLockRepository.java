package com.emmanuel.venn.loads.attempts;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface CustomerLockRepository extends JpaRepository<CustomerLockEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from CustomerLockEntity l where l.customerId = :customerId")
    Optional<CustomerLockEntity> lockByCustomerId(@Param("customerId") String customerId);
}
