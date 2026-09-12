package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

import java.math.BigDecimal;

public record FloatingNumber(double value) implements CalcNumber {

    /** 值对象自守不变量：非有限值不得存在于本类型中。工厂之外还有公开构造器，
     *  在这里拦住，才能保证 divide / modulo / factorial 不会在 BigDecimal.valueOf 上崩。
     *  Numbers.requireFinite 承担的是「哪一步运算溢出了」的定位职责，两者互补。 */
    public FloatingNumber {
        if (!Double.isFinite(value)) {
            throw CalcException.of(CalcErrorCode.NON_FINITE_RESULT,
                    "浮点值必须为有限值，实际为 " + value);
        }
    }

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
