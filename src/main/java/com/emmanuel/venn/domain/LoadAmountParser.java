package com.emmanuel.venn.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

public final class LoadAmountParser {

    private static final Pattern DOLLARS = Pattern.compile("\\$(\\d+)(\\.\\d{1,2})?");

    private LoadAmountParser() {}

    public static BigDecimal parse(String raw) {
        if (raw == null || !DOLLARS.matcher(raw).matches()) {
            throw new InvalidLoadException("load_amount must look like $123.45, got: " + raw);
        }
        BigDecimal amount = new BigDecimal(raw.substring(1));
        if (amount.signum() <= 0) {
            throw new InvalidLoadException("load_amount must be positive, got: " + raw);
        }
        return amount;
    }
}
