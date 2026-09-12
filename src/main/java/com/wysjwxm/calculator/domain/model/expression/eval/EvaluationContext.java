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
 * （那会抛出 INVALID_REQUEST，而正确的语义是 UNKNOWN_VARIABLE）。集合里的
 * 键在写入时已经过 VariableName 校验，因此这里按字符串查到的东西，
 * 必然是合法定义过的。
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
