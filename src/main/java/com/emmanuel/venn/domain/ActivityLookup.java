package com.emmanuel.venn.domain;

@FunctionalInterface
public interface ActivityLookup {

    Totals acceptedWithin(TimeRange range);
}
