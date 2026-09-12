package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.eval.AstInspection;
import com.wysjwxm.calculator.domain.model.expression.eval.EvaluationContext;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.Map;

/**
 * 求值用例：编排「施加策略 → 解析 → 求值 → 产出 Calculation」。
 *
 * <p>本类不含业务规则 —— 算术、优先级、定义域、函数语义都在领域层。
 * 这里只做编排与策略施加。
 *
 * <p>本期是 MVP：不落历史、不合并存储变量，因此没有历史写入与耗时统计
 * （Phase 2 补，见 docs/mvp-and-roadmap.md）。
 */
public class CalculationUseCase {

    private final ExpressionParser parser;
    private final ExpressionEvaluator evaluator;
    private final CalculationPolicy policy;

    public CalculationUseCase(ExpressionParser parser, ExpressionEvaluator evaluator,
                              CalculationPolicy policy) {
        this.parser = parser;
        this.evaluator = evaluator;
        this.policy = policy;
    }

    public Calculation calculate(CalculationCommand command) {
        ExpressionText text = buildExpressionText(command.expression());
        AngleUnit angleUnit = command.angleUnit() != null
                ? command.angleUnit()
                : policy.defaultAngleUnit();

        Expression ast = parser.parse(text);
        Map<String, CalcNumber> variables = command.variables();
        EvaluationContext context = variables.isEmpty()
                ? EvaluationContext.empty()
                : EvaluationContext.of(variables);
        CalcNumber result = evaluator.evaluate(ast, angleUnit, context);

        // angleUnit 仅在表达式真的用到了角度相关函数时才有记录价值（spec §7.3）
        AngleUnit recordedUnit = AstInspection.usesAngleSensitiveFunction(
                ast, evaluator.functionRegistry())
                ? angleUnit
                : null;

        return new Calculation(text, result, recordedUnit);
    }

    /**
     * 表达式长度上限在此强制：它是来自配置的资源保护策略，不是语言不变量，
     * 因此不属于 ExpressionText 的构造器职责。
     */
    private ExpressionText buildExpressionText(String raw) {
        ExpressionText text = new ExpressionText(raw);
        int limit = policy.maxExpressionLength();
        if (text.value().length() > limit) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    "expression 长度不得超过 " + limit + "，实际为 " + text.value().length());
        }
        return text;
    }
}
