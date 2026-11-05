package com.emmanuel.venn.velocity;

import java.time.Instant;

/** Half-open range: includes start, excludes end. */
public record TimeRange(Instant start, Instant end) {

    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(end);
    }
}
