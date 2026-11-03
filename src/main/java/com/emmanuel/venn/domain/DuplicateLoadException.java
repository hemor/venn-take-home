package com.emmanuel.venn.domain;

public class DuplicateLoadException extends RuntimeException {

    public DuplicateLoadException(String customerId, String loadId, Throwable cause) {
        super("Load " + loadId + " already recorded for customer " + customerId, cause);
    }
}
