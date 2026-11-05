package com.emmanuel.venn.velocity;

@FunctionalInterface
public interface ActivityLookup {

    Totals acceptedWithin(TimeRange range);
}
