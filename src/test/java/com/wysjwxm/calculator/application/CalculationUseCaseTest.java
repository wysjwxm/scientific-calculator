package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 求值用例的行为。本期是 MVP：用例只编排「策略 → 解析 → 求值 → 产出 Calculation」，
 * 不落历史、不合并存储变量，因此这里没有 id / 耗时 / 历史的断言（Phase 2 补）。
 */
class CalculationUseCaseTest {

    private final FunctionRegistry registry = new FunctionRegistry();
    private final Numbers numbers = new Numbers(34);
    private final CalculationPolicy policy = new CalculationPolicy(AngleUnit.DEGREE, 1000, 34);
    private final CalculationUseCase useCase = new CalculationUseCase(
            new ExpressionParser(), new ExpressionEvaluator(registry, numbers), policy);

    private CalcNumber num(String v) {
        return new DecimalNumber(new BigDecimal(v));
    }

    private Calculation calc(String expression) {
        return useCase.calculate(CalculationCommand.of(expression));
    }

    // ---------- 基本求值 ----------

    @Test
    void evaluatesExpressionAndReturnsResult() {
        Calculation record = calc("1+2");
        assertThat(record.result().toDecimal()).isEqualByComparingTo("3");
        assertThat(record.expression().value()).isEqualTo("1+2");
    }

    @Test
    void expressionIsTrimmedBeforeStoring() {
        assertThat(calc("  1+2  ").expression().value()).isEqualTo("1+2");
    }

    @Test
    void parseErrorCarriesPosition() {
        assertThatThrownBy(() -> calc("1+"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(2));
    }

    // ---------- 策略：表达式长度 ----------

    @Test
    void rejectsExpressionExceedingPolicyLimit() {
        CalculationPolicy tightPolicy = new CalculationPolicy(AngleUnit.DEGREE, 10, 34);
        CalculationUseCase tight = new CalculationUseCase(
                new ExpressionParser(), new ExpressionEvaluator(registry, numbers), tightPolicy);
        assertThatThrownBy(() -> tight.calculate(CalculationCommand.of("1+".repeat(20) + "1")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
                    assertThat(ce.getMessage()).contains("10");
                });
    }

    @Test
    void rejectsNullAndBlankExpression() {
        assertThatThrownBy(() -> calc(null)).isInstanceOf(CalcException.class);
        assertThatThrownBy(() -> calc("   ")).isInstanceOf(CalcException.class);
    }

    // ---------- 角度单位 ----------

    @Test
    void defaultsToPolicyAngleUnitWhenCommandOmitsIt() {
        Calculation record = calc("sin(30)");
        assertThat(record.result().toDouble())
                .isCloseTo(0.5, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(record.angleUnit()).isEqualTo(AngleUnit.DEGREE);
    }

    @Test
    void commandAngleUnitOverridesPolicy() {
        Calculation record = useCase.calculate(
                new CalculationCommand("sin(30)", AngleUnit.RADIAN, Map.of()));
        // 期望值用冻结字面量而不是 Math.sin(30)：后者就是 SIN 的实现本身，期望值与
        // 实现同源的话，实现变了期望值跟着变，两边一起错就没人发现。
        // 这个数是**规整后**的值：Numbers.floating() 把算出来的 double 规整到 15 位有效
        // 数字，30 弧度的原始 Math.sin ≈ -0.9880316240928617 经规整即此值。写的就是系统
        // 对外产出的那个数，不是未经规整的原始近似值。
        // （30 弧度 ≈ -0.988031624092862；DEGREE 与 RADIAN 在此给出完全不同的数。）
        assertThat(record.result().toDouble())
                .isCloseTo(-0.988031624092862, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(record.angleUnit()).isEqualTo(AngleUnit.RADIAN);
    }

    @Test
    void angleUnitIsNullForNonTrigonometricExpressions() {
        // spec §7.3：非三角记录该字段为 null
        assertThat(calc("1+2").angleUnit()).isNull();
        assertThat(calc("sqrt(16)").angleUnit()).isNull();
        assertThat(calc("hypot(3,4)").angleUnit()).isNull();
        assertThat(calc("atan2(1,1)").angleUnit()).isNotNull();
    }

    // ---------- 请求级变量 ----------

    @Test
    void requestVariablesAreUsable() {
        Calculation record = useCase.calculate(
                new CalculationCommand("x*2", null, Map.of("x", num("7"))));
        assertThat(record.result().toDecimal()).isEqualByComparingTo("14");
    }

    @Test
    void commandOfHelperProducesEmptyVariables() {
        assertThat(CalculationCommand.of("1+1").variables()).isEmpty();
        assertThat(CalculationCommand.of("1+1").angleUnit()).isNull();
    }

    @Test
    void commandVariablesAreDefensivelyCopied() {
        var mutable = new java.util.HashMap<String, CalcNumber>();
        mutable.put("x", num("5"));
        CalculationCommand command = new CalculationCommand("x", null, mutable);
        mutable.put("y", num("6"));
        assertThat(command.variables()).containsOnlyKeys("x");
    }

    @Test
    void nullVariablesAreTreatedAsEmpty() {
        Calculation record = useCase.calculate(new CalculationCommand("1+1", null, null));
        assertThat(record.result().toDecimal()).isEqualByComparingTo("2");
    }

    @Test
    void nullVariableKeysAndValuesAreRejectedWithInvalidRequest() {
        // 请求体 {"expression":"x*2","variables":{"x":null}} 经 Jackson 反序列化就得到
        // 含 null 值的 Map（可变的 HashMap），这条路径曾会以无消息的 NPE 逃成 HTTP 500。
        // 注意**不能用 Map.of(...) 构造测试数据**：Map.of 自己就拒收 null，
        // 那样测试会在构造入参时就抛 NPE 通过，断言的是 JDK 而不是我们的实现 —— 空转。
        Map<String, CalcNumber> withNullValue = new java.util.HashMap<>();
        withNullValue.put("x", null);
        Map<String, CalcNumber> withNullKey = new java.util.HashMap<>();
        withNullKey.put(null, num("1"));

        assertThatThrownBy(() -> new CalculationCommand("x*2", null, withNullValue))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
        assertThatThrownBy(() -> new CalculationCommand("x*2", null, withNullKey))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }
}
