package com.emmanuel.venn.loads.attempts;

import java.math.BigDecimal;

/** Query projection. The sum is null when there are no rows. */
interface AcceptedTotals {

    BigDecimal getTotal();

    long getLoads();
}
