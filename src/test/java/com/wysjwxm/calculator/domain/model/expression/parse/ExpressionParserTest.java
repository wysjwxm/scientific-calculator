package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.ExpressionPrinter;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionParserTest {

    private final ExpressionParser parser = new ExpressionParser();

    private String infix(String input) {
        return ExpressionPrinter.toInfix(parser.parse(new ExpressionText(input)));
    }

    // ---------- 结构与优先级 ----------

    @Test
    void multiplicationBindsTighterThanAddition() {
        assertThat(infix("1+2*3")).isEqualTo("(1 + (2 * 3))");
    }

    @Test
    void subtractionIsLeftAssociative() {
        assertThat(infix("1-2-3")).isEqualTo("((1 - 2) - 3)");
    }

    @Test
    void divisionIsLeftAssociative() {
        assertThat(infix("8/4/2")).isEqualTo("((8 / 4) / 2)");
    }

    @Test
    void powerIsRightAssociative() {
        // spec §6.3：2^3^2 == 2^(3^2) == 512
        assertThat(infix("2^3^2")).isEqualTo("(2 ^ (3 ^ 2))");
    }

    @Test
    void unaryMinusBindsLooserThanPower() {
        // spec §6.3：-2^2 == -(2^2) == -4，而不是 (-2)^2 == 4
        assertThat(infix("-2^2")).isEqualTo("(-(2 ^ 2))");
    }

    @Test
    void powerBindsTighterThanUnaryOnExponentSide() {
        assertThat(infix("2^-3")).isEqualTo("(2 ^ (-3))");
    }

    @Test
    void factorialBindsTighterThanAddition() {
        // spec §6.3：3! + 1 == 7
        assertThat(infix("3!+1")).isEqualTo("((3!) + 1)");
    }

    @Test
    void factorialBindsTighterThanUnaryMinus() {
        assertThat(infix("-3!")).isEqualTo("(-(3!))");
    }

    @Test
    void parenthesesOverridePrecedence() {
        assertThat(infix("(1+2)*3")).isEqualTo("((1 + 2) * 3)");
    }

    @Test
    void unaryPlusIsParsed() {
        assertThat(infix("+5")).isEqualTo("(+5)");
    }

    @Test
    void moduloIsParsed() {
        assertThat(infix("7%3")).isEqualTo("(7 % 3)");
    }

    // ---------- 函数调用 ----------

    @Test
    void parsesZeroArgumentCall() {
        assertThat(infix("now()")).isEqualTo("now()");
    }

    @Test
    void parsesSingleArgumentCall() {
        assertThat(infix("sin(30)")).isEqualTo("sin(30)");
    }

    @Test
    void parsesMultiArgumentCall() {
        assertThat(infix("log(8,2)")).isEqualTo("log(8, 2)");
    }

    @Test
    void parsesNestedCalls() {
        assertThat(infix("sin(cos(1))")).isEqualTo("sin(cos(1))");
    }

    @Test
    void argumentExpressionsParseFully() {
        assertThat(infix("max(1+2, 3*4)")).isEqualTo("max((1 + 2), (3 * 4))");
    }

    // ---------- 变量与字面量 ----------

    @Test
    void parsesBareIdentifierAsVariable() {
        assertThat(infix("x*2")).isEqualTo("(x * 2)");
    }

    @Test
    void parsesIdentifierStartingWithUnderscore() {
        assertThat(infix("_a + 1")).isEqualTo("(_a + 1)");
    }

    @Test
    void literalKeepsDecimalExactness() {
        LiteralExpr literal = (LiteralExpr) parser.parse(new ExpressionText("0.1"));
        assertThat(literal.value().isExact()).isTrue();
        assertThat(literal.value().toDecimal()).isEqualByComparingTo("0.1");
    }

    @Test
    void expressionTextIsTrimmedBeforeParsing() {
        assertThat(infix("  1+2  ")).isEqualTo("(1 + 2)");
    }

    // ---------- 错误与位置 ----------

    @Test
    void missingClosingParenReportsPosition() {
        // 位置 6 == 输入长度，即词法分析器补出的 EOF token 的位置 —— 本文件另两条
        // 「缺右括号」用例（"(1+2" -> 4、"max(1,2" -> 7）同样按输入长度取值。
        // brief 此处期望 7，但 "sin(30" 只有 6 个字符，0 基定位下取不到 7；
        // 该期望值与 brief 自己的另两条用例互相矛盾，已在 task-6-report.md 记录。
        assertThatThrownBy(() -> parser.parse(new ExpressionText("sin(30")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(6);
                });
    }

    @Test
    void unexpectedTokenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1 + * 2")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(4));
    }

    @Test
    void trailingOperatorReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1 +")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(3));
    }

    @Test
    void mismatchedParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("(1+2")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(4));
    }

    @Test
    void strayClosingParenIsRejected() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("(1+2))")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(5));
    }

    @Test
    void missingCallClosingParenReportsPosition() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("max(1,2")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(7));
    }

    @Test
    void trailingCommaInCallIsRejected() {
        assertThatThrownBy(() -> parser.parse(new ExpressionText("max(1,)")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(6));
    }

    // ---------- 超范围数字字面量：NumberFormatException 必须转成 PARSE_ERROR ----------
    // 词法分析器接受任意长度的指数数字串，因此下列字面量都是**合法 token**，
    // 而 new BigDecimal(lexeme) 会抛未检查的 NumberFormatException；不拦截的话它会
    // 一路逃到接口层变成 HTTP 500。三种 lexeme 覆盖 BigDecimal 的三条构造失败路径。

    @Test
    void oversizedExponentIsReportedAsParseError() {
        // Exponent overflow
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1E+2147483648")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(0);
                });
    }

    @Test
    void exponentWithTooManyDigitsIsReportedAsParseError() {
        // Too many nonzero exponent digits
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1E+99999999999999")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(0);
                });
    }

    @Test
    void scaleOutOfRangeLiteralIsReportedAsParseErrorAtItsOwnPosition() {
        // Scale out of range；位置指向出问题的字面量本身，而不是表达式开头
        assertThatThrownBy(() -> parser.parse(new ExpressionText("1+1E-2147483648")))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(2);
                });
    }

    // ---------- 无状态（并发安全） ----------

    @Test
    void parserIsStatelessAndSafeForConcurrentUse() throws Exception {
        // 同一个解析器实例被多线程共享 —— Spring 单例场景。若游标是实例字段，
        // 这里的断言会因线程互相踩踏而失败。
        int threads = 8;
        int perThread = 500;
        List<Callable<Boolean>> tasks = IntStream.range(0, threads)
                .mapToObj(t -> (Callable<Boolean>) () -> {
                    for (int i = 0; i < perThread; i++) {
                        if (!infix("1+2*3").equals("(1 + (2 * 3))")) {
                            return false;
                        }
                        if (!infix("2^3^2").equals("(2 ^ (3 ^ 2))")) {
                            return false;
                        }
                        if (!infix("sin(30)+1").equals("(sin(30) + 1)")) {
                            return false;
                        }
                    }
                    return true;
                })
                .toList();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            for (Future<Boolean> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS))
                        .as("并发解析结果被污染 —— 解析器可能不是无状态的")
                        .isTrue();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
