package com.emmanuel.venn.velocity;

import java.time.Instant;

/** Half-open range: includes start, excludes end. */
public record TimeRange(Instant start, Instant end) {}
