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

    /** 值级断言：函数在指定角度制下的返回值。
     *  函数结果要经过 15 位规整（见 Numbers.floating），所以一律用容差比较而非精确相等。 */
    private void assertValue(String name, AngleUnit unit, double expected, double... args) {
        assertThat(apply(name, unit, args).toDouble())
                .isCloseTo(expected, org.assertj.core.data.Offset.offset(1e-12));
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

    @Test
    void inverseTrigonometryIsAngleSensitiveForAcosAndAtanToo() {
        // isInverseTrigonometric() 硬编码了 ASIN || ACOS || ATAN 三个常量，而此前
        // 只有 asin 有值断言 —— 缩成 `this == ASIN` 不会有任何测试变红，角度制下
        // acos(0.5) 会退回弧度值 1.5620695697691096、atan(1) 会退回 0.017451520651465824。
        // 三个函数各自钉住两制下的值，缩小那个 || 就会立刻变红。
        assertValue("asin", AngleUnit.DEGREE, 30.0, 0.5);
        assertValue("acos", AngleUnit.DEGREE, 60.0, 0.5);
        assertValue("atan", AngleUnit.DEGREE, 45.0, 1);
        assertValue("asin", AngleUnit.RADIAN, Math.asin(0.5), 0.5);
        assertValue("acos", AngleUnit.RADIAN, Math.acos(0.5), 0.5);
        assertValue("atan", AngleUnit.RADIAN, Math.PI / 4, 1);
    }

    @Test
    void hyperbolicFunctionsIgnoreAngleUnitAtNonZeroInput() {
        // 原断言全部取 x = 0，而 0 是 Math.toRadians / Math.toDegrees 的不动点 ——
        // 无论实现是否误把双曲函数当角度换算，x = 0 的断言都会通过。
        // 换到 x = 1 并把两种角度制都钉在同一个期望值上：只要有一制做了角度换算，
        // 结果就不再等于该字面量，测试必红。
        assertValue("sinh", AngleUnit.DEGREE, 1.1752011936438014, 1);
        assertValue("sinh", AngleUnit.RADIAN, 1.1752011936438014, 1);
        assertValue("cosh", AngleUnit.DEGREE, 1.543080634815244, 1);
        assertValue("cosh", AngleUnit.RADIAN, 1.543080634815244, 1);
        assertValue("tanh", AngleUnit.DEGREE, 0.7615941559557649, 1);
        assertValue("tanh", AngleUnit.RADIAN, 0.7615941559557649, 1);
        assertValue("asinh", AngleUnit.DEGREE, 0.881373587019543, 1);
        assertValue("asinh", AngleUnit.RADIAN, 0.881373587019543, 1);
    }

    @Test
    void atan2PinsArgumentOrderWithAsymmetricInputs() {
        // 唯一的既有断言用的是 atan2(1, 1) —— 对称入参，把 applyAsDouble(y, x) 写成
        // (x, y) 结果完全相同。用非对称入参：y/x 顺序写反会得到 63.43494882292201。
        assertValue("atan2", AngleUnit.DEGREE, 26.56505117707799, 1, 2);
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

    @Test
    void tanAndAtanHaveValueLevelAssertions() {
        // tan 与 atan 此前没有任何值正确性断言：把 TAN 换成 Math::tanh、
        // ATAN 换成 Math::tan 之后整套测试仍然全绿。这里各自钉一个典型值。
        assertValue("tan", AngleUnit.DEGREE, 1.0, 45);
        assertValue("atan", AngleUnit.RADIAN, Math.PI / 4, 1);
    }

    @Test
    void normalizedFunctionResultsLandExactlyOnThePromisedValues() {
        // 这一组是 MVP 对外承诺的精确值（不是「接近」）：函数结果直连
        // Numbers.floating，而 floating 负责把 15 位之后的误差伪影规整掉。
        // 刻意用 isEqualTo —— 规整后这些值恰好落在规范 double 上。
        assertThat(apply("sin", AngleUnit.DEGREE, 30).toDouble()).isEqualTo(0.5);
        assertThat(apply("cos", AngleUnit.DEGREE, 60).toDouble()).isEqualTo(0.5);
        assertThat(apply("tan", AngleUnit.DEGREE, 45).toDouble()).isEqualTo(1.0);
        assertThat(apply("asin", AngleUnit.DEGREE, 0.5).toDouble()).isEqualTo(30.0);
        assertThat(apply("acos", AngleUnit.DEGREE, 0.5).toDouble()).isEqualTo(60.0);
    }

    @Test
    void asinhAcceptsInputsUpToItsDeclaredMagnitudeBound() {
        // spec §5.3.1 为反双曲函数声明的自变量上界是 |x| <= sqrt(Double.MAX_VALUE) ≈ 1.34e154；
        // 1e154 在范围内，必须给出正确值。
        assertValue("asinh", AngleUnit.RADIAN, 355.291251501643, 1e154);
    }

    @Test
    void asinhRejectsInputsBeyondItsDeclaredMagnitudeBound() {
        // 实现里 x * x 会先溢出成 Infinity，所以上界是**有意公开**的行为而非公式错
        // （spec §5.3.3：拒收优于伪装）。超出上界即拒收，且错误码是 NON_FINITE_RESULT。
        assertThatThrownBy(() -> apply("asinh", AngleUnit.RADIAN, 1e155))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.NON_FINITE_RESULT);
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

    @Test
    void roundRejectsMagnitudesBeyondLongRange() {
        // round 的返回语义绑定在 64 位整数上：|x| >= 2^63 时 Math.round 饱和到
        // Long.MAX_VALUE，静默给出 9.223372036854776E18 这种「看起来像答案」的错值。
        // 定义域约束 Domain.LONG_RANGE 把它挡在求值之前。
        assertDomainError("round", 1e30);
        assertDomainError("round", -1e30);
    }

    @Test
    void roundBehaviourInsideLongRangeIsUnchanged() {
        // 边界是 9.223372036854776E18（2^63），1e18 远在范围内；负半轴同样是
        // 「加 0.5 后向下取整」的既有语义（round(-2.5) = -2）。
        assertValue("round", AngleUnit.RADIAN, 1e18, 1e18);
        assertThat(apply("round", AngleUnit.RADIAN, -2.5).toDouble()).isEqualTo(-2.0);
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

    @Test
    void angleSensitiveFunctionsAreExactlyTheTrigonometrySet() {
        // 「哪些函数受角度制影响」这件事被表达在三处：枚举的 angleSensitive 标志、
        // UnaryFunction.isInverseTrigonometric() 手写的三个常量、BinaryFunction 里
        // 直接写的 this == ATAN2。三者今天一致，但漏改任何一处都没有信号。
        // 这条断言按标志过滤出精确集合，把三者共同的可观测面钉死。
        assertThat(UnaryFunction.values())
                .filteredOn(UnaryFunction::angleSensitive)
                .extracting(UnaryFunction::functionName)
                .containsExactlyInAnyOrder("sin", "cos", "tan", "asin", "acos", "atan");
        assertThat(BinaryFunction.values())
                .filteredOn(BinaryFunction::angleSensitive)
                .extracting(BinaryFunction::functionName)
                .containsExactlyInAnyOrder("atan2");
    }
}
