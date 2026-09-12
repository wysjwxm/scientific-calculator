package com.wysjwxm.calculator.domain.model.number;

import java.math.BigDecimal;
import java.util.Objects;

public record DecimalNumber(BigDecimal value) implements CalcNumber {

    public DecimalNumber {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public double toDouble() {
        return value.doubleValue();
    }

    @Override
    public BigDecimal toDecimal() {
        return value;
    }

    @Override
    public boolean isExact() {
        return true;
    }
}
