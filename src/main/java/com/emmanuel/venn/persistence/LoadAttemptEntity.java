package com.emmanuel.venn.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "load_attempt")
class LoadAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "load_id", nullable = false)
    private String loadId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    protected LoadAttemptEntity() {}

    LoadAttemptEntity(String loadId, String customerId, BigDecimal amount, Instant occurredAt,
            AttemptStatus status, String rejectionReason) {
        this.loadId = loadId;
        this.customerId = customerId;
        this.amount = amount;
        this.occurredAt = occurredAt;
        this.status = status;
        this.rejectionReason = rejectionReason;
    }
}
