package com.emmanuel.venn.persistence;

import java.math.BigDecimal;

record AcceptedTotals(BigDecimal total, long count) {

    AcceptedTotals {
        total = total == null ? BigDecimal.ZERO : total;
    }
}
