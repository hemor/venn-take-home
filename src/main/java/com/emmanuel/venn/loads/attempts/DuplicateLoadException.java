package com.emmanuel.venn.loads.attempts;

public class DuplicateLoadException extends RuntimeException {

    public DuplicateLoadException(String customerId, String loadId, Throwable cause) {
        super("Load " + loadId + " already recorded for customer " + customerId, cause);
    }
}
