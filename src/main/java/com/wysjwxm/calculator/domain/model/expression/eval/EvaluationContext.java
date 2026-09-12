package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.Map;
import java.util.Optional;

/**
 * 求值期的变量来源。领域通过这个接口拿到变量，从而不必认识聚合根 ——
 * 依赖方向因此保持单向。
 *
 * <p>按**原始字符串**查找而非 {@code VariableName}：表达式里的标识符是任意
 * 词法单元，求值器不应因为 "sin(sin)" 里的内层 sin 而构造一个名字值对象
 * （那会抛出 INVALID_REQUEST，而正确的语义是 UNKNOWN_VARIABLE）。{@code VariableName}
 * 与它的校验属 Phase 2（决策 D13），当前并不存在：写入端（{@code CalculationCommand}）
 * 只拒收保留名与 null，不做任何格式校验。这里查不到非标识符的键，原因不在写入端，
 * 而在**词法器产不出那种 token** —— 求值器只按表达式里的标识符查找。
 */
@FunctionalInterface
public interface EvaluationContext {

    Optional<CalcNumber> lookup(String name);

    static EvaluationContext of(Map<String, CalcNumber> variables) {
        Map<String, CalcNumber> snapshot = Map.copyOf(variables);
        return name -> Optional.ofNullable(snapshot.get(name));
    }

    static EvaluationContext empty() {
        return name -> Optional.empty();
    }
}
