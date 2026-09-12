package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 本设计数值模型存在的理由：精确路径必须真的精确。
 * 这些断言用 equals 而非 delta 比较 —— delta 比较就等于放弃了精度保证。
 */
class PrecisionTest {

    private final Numbers numbers = new Numbers(34);

    @Test
    void decimalAdditionIsExact() {
        // 若走 double，这里会得到 0.30000000000000004
        CalcNumber a = numbers.of(new BigDecimal("0.1"));
        CalcNumber b = numbers.of(new BigDecimal("0.2"));
        assertThat(numbers.add(a, b)).isEqualTo(numbers.of(new BigDecimal("0.3")));
    }

    @Test
    void decimalAdditionNeverDegradesToFloating() {
        CalcNumber result = numbers.add(
                numbers.of(new BigDecimal("0.1")),
                numbers.of(new BigDecimal("0.2")));
        assertThat(result).isInstanceOf(DecimalNumber.class);
        assertThat(result.isExact()).isTrue();
    }

    @Test
    void oneThirdUsesConfiguredPrecision() {
        CalcNumber result = numbers.divide(numbers.of(1L), numbers.of(3L));
        assertThat(result.toDecimal())
                .isEqualTo(new BigDecimal("0.3333333333333333333333333333333333"));
    }

    @Test
    void mixingWithFloatingDegradesToFloating() {
        CalcNumber result = numbers.add(numbers.of(new BigDecimal("0.1")), numbers.floating(0.2));
        assertThat(result).isInstanceOf(FloatingNumber.class);
        assertThat(result.isExact()).isFalse();
    }

    @Test
    void powerWithNonNegativeIntegerExponentStaysExact() {
        CalcNumber result = numbers.power(numbers.of(new BigDecimal("0.1")), numbers.of(3L));
        assertThat(result).isInstanceOf(DecimalNumber.class);
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("0.001"));
    }

    @Test
    void powerWithFractionalExponentGoesFloating() {
        CalcNumber result = numbers.power(numbers.of(2L), numbers.floating(0.5));
        assertThat(result).isInstanceOf(FloatingNumber.class);
        assertThat(result.toDouble()).isCloseTo(Math.sqrt(2),
                org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> numbers.divide(numbers.of(1L), numbers.of(0L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    void moduloByZeroThrows() {
        assertThatThrownBy(() -> numbers.modulo(numbers.of(1L), numbers.of(0L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    void factorialOfNegativeThrowsDomainError() {
        assertThatThrownBy(() -> numbers.factorial(numbers.of(-1L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void factorialOfNonIntegerThrowsDomainError() {
        assertThatThrownBy(() -> numbers.factorial(numbers.of(new BigDecimal("1.5"))))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void factorialAboveBoundThrowsNonFinite() {
        assertThatThrownBy(() -> numbers.factorial(numbers.of(171L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void factorialBoundaries() {
        assertThat(numbers.factorial(numbers.of(0L))).isEqualTo(numbers.of(1L));
        assertThat(numbers.factorial(numbers.of(5L))).isEqualTo(numbers.of(120L));
    }
}
