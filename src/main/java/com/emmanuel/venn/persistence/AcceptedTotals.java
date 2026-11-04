package com.emmanuel.venn.persistence;

import java.math.BigDecimal;

/** Query projection. The sum is null when there are no rows. */
interface AcceptedTotals {

    BigDecimal getTotal();

    long getLoads();
}
