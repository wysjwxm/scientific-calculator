package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionRegistryTest {

    private final FunctionRegistry registry = new FunctionRegistry();
    private final Numbers numbers = new Numbers(34);

    private CalcNumber apply(String name, AngleUnit unit, double... args) {
        List<CalcNumber> values = Arrays.stream(args).mapToObj(numbers::floating).toList();
        return registry.find(name).orElseThrow().apply(values, unit, numbers);
    }

    @Test
    void registersExactlyTwentyThreeUnaryAndFiveBinary() {
        assertThat(registry.unaryNames()).hasSize(23);
        assertThat(registry.binaryNames()).hasSize(5);
    }

    @Test
    void functionNameIsTheLanguageNameNotTheEnumIdentifier() {
        assertThat(registry.find("log10").orElseThrow().functionName()).isEqualTo("log10");
        assertThat(registry.find("log2").orElseThrow().functionName()).isEqualTo("log2");
        assertThat(registry.find("atan2").orElseThrow().functionName()).isEqualTo("atan2");
    }

    @Test
    void doesNotRegisterRedundantFunctionSpellings() {
        assertThat(registry.find("pow")).isEmpty();
        assertThat(registry.find("mod")).isEmpty();
        assertThat(registry.find("fact")).isEmpty();
    }

    @Test
    void unknownFunctionIsNotFound() {
        assertThat(registry.find("nope")).isEmpty();
    }

    // ---------- 角度单位 ----------

    @Test
    void degreeAndRadianGiveDifferentSineResults() {
        assertThat(apply("sin", AngleUnit.DEGREE, 30).toDouble())
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("sin", AngleUnit.RADIAN, 30).toDouble())
                .isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void degreesAreIgnoredByNonTrigonometricFunctions() {
        assertThat(apply("sqrt", AngleUnit.DEGREE, 4).toDouble()).isEqualTo(2.0);
        assertThat(apply("sqrt", AngleUnit.RADIAN, 4).toDouble()).isEqualTo(2.0);
    }

    @Test
    void inverseTrigRespectsAngleUnit() {
        assertThat(apply("asin", AngleUnit.DEGREE, 0.5).toDouble())
                .isCloseTo(30.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(apply("asin", AngleUnit.RADIAN, 0.5).toDouble())
                .isCloseTo(Math.asin(0.5), org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void atan2RespectsAngleUnit() {
        assertThat(apply("atan2", AngleUnit.DEGREE, 1, 1).toDouble())
                .isCloseTo(45.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    // ---------- 各函数 ----------

    @Test
    void binaryFunctionsWork() {
        assertThat(apply("hypot", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(5.0);
        assertThat(apply("max", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(4.0);
        assertThat(apply("min", AngleUnit.RADIAN, 3, 4).toDouble()).isEqualTo(3.0);
        assertThat(apply("log", AngleUnit.RADIAN, 8, 2).toDouble())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void unaryFunctionsWork() {
        assertThat(apply("abs", AngleUnit.RADIAN, -3).toDouble()).isEqualTo(3.0);
        assertThat(apply("floor", AngleUnit.RADIAN, 2.7).toDouble()).isEqualTo(2.0);
        assertThat(apply("ceil", AngleUnit.RADIAN, 2.1).toDouble()).isEqualTo(3.0);
        assertThat(apply("round", AngleUnit.RADIAN, 2.5).toDouble()).isEqualTo(3.0);
        assertThat(apply("sign", AngleUnit.RADIAN, -9).toDouble()).isEqualTo(-1.0);
        assertThat(apply("exp", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(1.0);
        assertThat(apply("ln", AngleUnit.RADIAN, 1).toDouble()).isEqualTo(0.0);
        assertThat(apply("log10", AngleUnit.RADIAN, 100).toDouble())
                .isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("log2", AngleUnit.RADIAN, 8).toDouble())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("cbrt", AngleUnit.RADIAN, 27).toDouble())
                .isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(apply("cos", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(1.0);
        assertThat(apply("tanh", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(0.0);
        assertThat(apply("asinh", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(0.0);
        assertThat(apply("cosh", AngleUnit.RADIAN, 0).toDouble()).isEqualTo(1.0);
    }

    // ---------- 定义域 ----------

    private void assertDomainError(String name, double... args) {
        assertThatThrownBy(() -> apply(name, AngleUnit.RADIAN, args))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void sqrtOfNegativeIsDomainError() {
        assertDomainError("sqrt", -1);
    }

    @Test
    void logarithmOfNonPositiveIsDomainError() {
        assertDomainError("ln", 0);
        assertDomainError("ln", -1);
        assertDomainError("log10", 0);
        assertDomainError("log2", -1);
    }

    @Test
    void logWithInvalidBaseIsDomainError() {
        assertDomainError("log", 8, 0);
        assertDomainError("log", 8, 1);
        assertDomainError("log", -1, 2);
    }

    @Test
    void asinAndAcosOutOfRangeAreDomainErrors() {
        assertDomainError("asin", 2);
        assertDomainError("acos", -2);
    }

    @Test
    void acoshBelowOneIsDomainError() {
        assertDomainError("acosh", 0.5);
    }

    @Test
    void atanhOutOfOpenIntervalIsDomainError() {
        assertDomainError("atanh", 1);
        assertDomainError("atanh", -1);
    }

    @Test
    void atanhBoundariesAreAccepted() {
        // 期望值写成字面量而非 Math.atanh(0.5)：Java 17 的 Math 没有 atanh（JDK 20 才加入），
        // 引用它编译不过。0.5493061443340549 是 atanh(0.5) = ½·ln 3 的双精度真值。
        assertThat(apply("atanh", AngleUnit.RADIAN, 0.5).toDouble())
                .isCloseTo(0.5493061443340549, org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 元数 ----------

    @Test
    void unaryFunctionRejectsWrongArity() {
        assertThatThrownBy(() -> registry.find("sin").orElseThrow()
                .apply(List.of(numbers.of(1L), numbers.of(2L)), AngleUnit.RADIAN, numbers))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void binaryFunctionRejectsWrongArity() {
        assertThatThrownBy(() -> registry.find("hypot").orElseThrow()
                .apply(List.of(numbers.of(1L)), AngleUnit.RADIAN, numbers))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void angleSensitivityFlagsAreCorrect() {
        assertThat(registry.find("sin").orElseThrow().angleSensitive()).isTrue();
        assertThat(registry.find("atan2").orElseThrow().angleSensitive()).isTrue();
        assertThat(registry.find("sqrt").orElseThrow().angleSensitive()).isFalse();
        assertThat(registry.find("log").orElseThrow().angleSensitive()).isFalse();
        assertThat(registry.find("sinh").orElseThrow().angleSensitive()).isFalse();
    }
}
