package com.emmanuel.venn.loads.attempts;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_lock")
class CustomerLockEntity {

    @Id
    private String customerId;

    protected CustomerLockEntity() {}

    CustomerLockEntity(String customerId) {
        this.customerId = customerId;
    }
}
