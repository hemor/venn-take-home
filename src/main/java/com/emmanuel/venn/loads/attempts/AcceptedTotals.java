package com.emmanuel.venn.loads.attempts;

/** Query projection. The total is null when there are no rows. */
interface AcceptedTotals {

    Long getTotal();

    long getLoads();
}
