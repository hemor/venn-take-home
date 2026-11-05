package com.emmanuel.venn.money;

import java.math.BigDecimal;
import java.util.Currency;

public final class TestMoney {

    public static final Currency CAD = Currency.getInstance("CAD");

    private TestMoney() {}

    public static Money cad(String major) {
        return Money.ofMajor(new BigDecimal(major), CAD);
    }
}
