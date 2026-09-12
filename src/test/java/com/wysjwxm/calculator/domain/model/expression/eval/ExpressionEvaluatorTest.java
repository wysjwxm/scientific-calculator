package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionEvaluatorTest {

    private final ExpressionParser parser = new ExpressionParser();
    private final FunctionRegistry registry = new FunctionRegistry();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(registry, new Numbers(34));

    private Expression parse(String input) {
        return parser.parse(new ExpressionText(input));
    }

    private CalcNumber eval(String input) {
        return evaluator.evaluate(parse(input), AngleUnit.DEGREE, EvaluationContext.empty());
    }

    private CalcNumber eval(String input, Map<String, CalcNumber> variables) {
        return evaluator.evaluate(parse(input), AngleUnit.DEGREE, EvaluationContext.of(variables));
    }

    private double num(String input) {
        return eval(input).toDouble();
    }

    // ---------- 优先级（spec §6.3 三条规则端到端验证） ----------

    @Test
    void arithmeticFollowsPrecedence() {
        assertThat(num("1+2*3")).isEqualTo(7.0);
        assertThat(num("(1+2)*3")).isEqualTo(9.0);
    }

    @Test
    void powerIsRightAssociative() {
        // 2^3^2 == 2^9 == 512；若左结合会得到 64
        assertThat(num("2^3^2")).isEqualTo(512.0);
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        assertThat(num("-2^2")).isEqualTo(-4.0);
    }

    @Test
    void factorialBindsTighterThanAddition() {
        assertThat(num("3!+1")).isEqualTo(7.0);
    }

    @Test
    void moduloAndUnaryPlusWork() {
        assertThat(num("7%3")).isEqualTo(1.0);
        assertThat(num("+5")).isEqualTo(5.0);
    }

    // ---------- 精度端到端 ----------

    @Test
    void decimalArithmeticStaysExactEndToEnd() {
        CalcNumber result = eval("0.1+0.2");
        assertThat(result.isExact()).isTrue();
        assertThat(result.toDecimal()).isEqualTo(new BigDecimal("0.3"));
    }

    @Test
    void specAcceptanceExpression() {
        // spec §14 验收标准第 3 条
        assertThat(num("1 + 2 * sin(30) ^ 2"))
                .isCloseTo(1.5, org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 变量与常量 ----------

    @Test
    void resolvesVariablesFromContext() {
        Numbers numbers = new Numbers(34);
        assertThat(eval("x*2", Map.of("x", numbers.of(new BigDecimal("5")))).toDouble())
                .isEqualTo(10.0);
    }

    @Test
    void resolvesBuiltInConstants() {
        assertThat(num("pi")).isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(num("e")).isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void undefinedVariableThrows() {
        assertThatThrownBy(() -> eval("y + 1"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_VARIABLE);
    }

    @Test
    void variableNameThatLooksLikeAFunctionIsReportedAsUnknownVariable() {
        // sin(sin) 里的内层 sin 是裸标识符。它必须是 UNKNOWN_VARIABLE（求值期的
        // 未定义），而不是 INVALID_REQUEST —— 求值器按字符串查表，不做名字构造。
        assertThatThrownBy(() -> eval("sin(sin)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_VARIABLE);
    }

    // ---------- 函数分派 ----------

    @Test
    void dispatchesToFunctions() {
        assertThat(num("sqrt(16)")).isEqualTo(4.0);
        assertThat(num("max(3,4)")).isEqualTo(4.0);
        assertThat(num("log(8,2)")).isCloseTo(3.0, org.assertj.core.data.Offset.offset(1e-12));
        assertThat(num("abs(-3)")).isEqualTo(3.0);
        assertThat(num("sqrt(sqrt(16))")).isEqualTo(2.0);
    }

    @Test
    void unknownFunctionThrows() {
        assertThatThrownBy(() -> eval("nope(1)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.UNKNOWN_FUNCTION);
    }

    @Test
    void wrongArityThrowsInvalidRequest() {
        assertThatThrownBy(() -> eval("sin(1,2)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    // ---------- 角度单位 ----------

    @Test
    void radianModeChangesTrigResults() {
        CalcNumber radian = evaluator.evaluate(parse("sin(30)"), AngleUnit.RADIAN,
                EvaluationContext.empty());
        assertThat(radian.toDouble())
                .isCloseTo(Math.sin(30), org.assertj.core.data.Offset.offset(1e-12));
    }

    // ---------- 错误传播 ----------

    @Test
    void divisionByZeroThrows() {
        assertThatThrownBy(() -> eval("1/0"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
    }

    @Test
    void domainErrorPropagates() {
        assertThatThrownBy(() -> eval("sqrt(-1)"))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.DOMAIN_ERROR);
    }

    @Test
    void overflowingPowerIsRejectedNotSilentlyInfinite() {
        assertThatThrownBy(() -> eval("9^9^9"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).code())
                        .isIn(CalcErrorCode.NON_FINITE_RESULT, CalcErrorCode.DOMAIN_ERROR));
    }

    // ---------- AST 内省 ----------

    @Test
    void detectsAngleSensitiveUsage() {
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("sin(30)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("1+2"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("sqrt(2)"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("1+sin(2)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("max(sin(1),2)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("-sin(1)"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("sin(1)!"), registry)).isTrue();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("hypot(1,2)"), registry)).isFalse();
        assertThat(AstInspection.usesAngleSensitiveFunction(parse("atan2(1,2)"), registry)).isTrue();
    }
}
