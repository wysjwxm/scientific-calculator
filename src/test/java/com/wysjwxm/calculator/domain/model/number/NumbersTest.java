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
        // 9^9 = 387420489 远超结果位数预算，请求被提前引到浮点路径，由 requireFinite 拒收
        CalcNumber nine = numbers.of(9L);
        CalcNumber exponent = numbers.power(nine, nine);
        assertThatThrownBy(() -> numbers.power(nine, exponent))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void computedFloatingResultsAreNormalized() {
        // sin(30°) 的原始 double 是 0.49999999999999994；经过规整应为 0.5
        double raw = Math.sin(Math.toRadians(30));
        assertThat(raw).isNotEqualTo(0.5);
        assertThat(numbers.multiply(numbers.floating(raw), numbers.of(1L)).toDouble())
                .isEqualTo(0.5);
    }

    @Test
    void nestedHugePowerIsRejectedInsteadOfBlowingUp() {
        // (9^10000)^10000 的精确结果约 9542 万位：必须快速拒绝，而不是硬算到 OOM
        CalcNumber inner = numbers.power(numbers.of(9L), numbers.of(10_000L));
        assertThatThrownBy(() -> numbers.power(inner, numbers.of(10_000L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void floatingFactoryRejectsNonFinite() {
        assertThatThrownBy(() -> numbers.floating(Double.NaN))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
        assertThatThrownBy(() -> numbers.floating(Double.POSITIVE_INFINITY))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void exactPowerToleratesMultiDigitSignificand() {
        // 预算是「未缩放位数」：9.0 的 precision 为 2，2 × 10000 = 20000 仍在预算内。
        // 9.0^10000 与 9^10000 是同一个数，字面量的 scale 不该把它推向浮点路径。
        assertThat(numbers.power(numbers.of(new BigDecimal("9.0")), numbers.of(10_000L)))
                .isInstanceOf(DecimalNumber.class);
        assertThat(numbers.power(numbers.of(new BigDecimal("1.5")), numbers.of(5_001L)))
                .isInstanceOf(DecimalNumber.class);
        // 1.0^9999 曾经因为预算误判而静默降级成 FloatingNumber(1.0)
        assertThat(numbers.power(numbers.of(new BigDecimal("1.0")), numbers.of(9_999L)))
                .isInstanceOf(DecimalNumber.class);
    }

    @Test
    void digitBudgetStillBitesJustAboveItsBound() {
        // 1.5 的未缩放位数是 2：2 × 50001 = 100002 > 100000，必须降级并被 requireFinite 拒收
        assertThatThrownBy(() -> numbers.power(numbers.of(new BigDecimal("1.5")), numbers.of(50_001L)))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void floatingNumberRejectsNonFiniteDirectly() {
        // 绕过 Numbers 工厂直接构造也必须被拒：否则 divide/modulo/factorial 会抛
        // NumberFormatException，变成 500 INTERNAL_ERROR
        assertThatThrownBy(() -> new FloatingNumber(Double.NaN))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
        assertThatThrownBy(() -> new FloatingNumber(Double.POSITIVE_INFINITY))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
    }

    @Test
    void normalizationDoesNotFalselyRejectTopOfDoubleRange() {
        // 规整的进位会把 Double.MAX_VALUE 推成 Infinity；此时必须保留原值
        assertThat(numbers.multiply(numbers.floating(Double.MAX_VALUE), numbers.of(1L)).toDouble())
                .isEqualTo(Double.MAX_VALUE);
    }
}
