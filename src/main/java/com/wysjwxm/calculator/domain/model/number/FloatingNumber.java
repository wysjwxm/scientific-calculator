package com.wysjwxm.calculator.domain.model.number;

import java.math.BigDecimal;

public record FloatingNumber(double value) implements CalcNumber {

    @Override
    public double toDouble() {
        return value;
    }

    @Override
    public BigDecimal toDecimal() {
        return BigDecimal.valueOf(value);
    }

    @Override
    public boolean isExact() {
        return false;
    }
}
