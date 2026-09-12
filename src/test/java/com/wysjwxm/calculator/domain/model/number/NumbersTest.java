package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NumbersTest {

    private final Numbers numbers = new Numbers(34);

    @Test
    void exactDivisionProducesExactResultWithoutTruncation() {
        CalcNumber result = numbers.divide(numbers.of(new BigDecimal("1")), numbers.of(new BigDecimal("8")));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("0.125"));
    }

    @Test
    void subtractionAndMultiplicationAreExact() {
        assertThat(numbers.subtract(numbers.of(new BigDecimal("0.3")), numbers.of(new BigDecimal("0.1"))))
                .isEqualTo(numbers.of(new BigDecimal("0.2")));
        assertThat(numbers.multiply(numbers.of(new BigDecimal("0.1")), numbers.of(new BigDecimal("0.2"))))
                .isEqualTo(numbers.of(new BigDecimal("0.02")));
    }

    @Test
    void moduloIsExactForDecimals() {
        CalcNumber result = numbers.modulo(numbers.of(new BigDecimal("5.5")), numbers.of(new BigDecimal("2")));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("1.5"));
    }

    @Test
    void negateOnDecimalStaysExact() {
        assertThat(numbers.negate(numbers.of(new BigDecimal("0.1"))))
                .isEqualTo(numbers.of(new BigDecimal("-0.1")));
    }

    @Test
    void nonFiniteResultThrows() {
        assertThatThrownBy(() -> numbers.multiply(numbers.floating(1e308), numbers.floating(10)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void rejectNonPositivePrecision() {
        assertThatThrownBy(() -> new Numbers(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decimalNeverConvertsSilentlyThroughDouble() {
        // double 无法精确表示 9007199254740993，走 decimal 路径仍然精确
        CalcNumber result = numbers.add(
                numbers.of(new BigDecimal("9007199254740993")), numbers.of(1L));
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("9007199254740994"));
    }

    @Test
    void hugePowerFallsBackToFloatingWithoutThrowingArithmeticException() {
        // 9^9 = 387420489 可装进 int，但 BigDecimal.pow 会因结果过大而抛 ArithmeticException
        CalcNumber nine = numbers.of(9L);
        CalcNumber exponent = numbers.power(nine, nine);
        assertThatThrownBy(() -> numbers.power(nine, exponent))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }
}
