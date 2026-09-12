package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.function.ReservedNames;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

import java.util.Map;

/**
 * 求值用例的入参。由接口层（防腐层）从 JSON 翻译而来。
 *
 * <p>注意 {@code variables} 的键仍是原始字符串 —— 本期的变量只作为**请求级
 * 临时量**进入求值上下文，没有变量存储，因此不存在第二个入口。键受**禁用集合**
 * （函数名 ∪ 保留常量名，spec §6.4）约束：落在集合内（如 {@code {"pi": 3}}）返回
 * 400 {@code INVALID_REQUEST}，与 spec :372 一致。
 *
 * <p>按 D13，Phase 2 补上 {@code VariableName} 值对象后，这个命名校验应迁移到该值
 * 对象的构造器里。当前放在这里是权宜：MVP 只有请求级这一个变量入口，还不需要
 * 一个专门的名字类型（详见 docs/mvp-and-roadmap.md 的已知取舍）。
 *
 * @param angleUnit null 表示未指定，取策略缺省值
 * @param variables 求值期可见的请求级变量；null 与空 Map 都视为「无变量」
 */
public record CalculationCommand(String expression, AngleUnit angleUnit,
                                 Map<String, CalcNumber> variables) {

    public CalculationCommand {
        if (variables == null) {
            variables = Map.of();
        } else {
            // Map.copyOf 对 null 键/值抛的是**无消息的** NullPointerException，而请求体
            // {"variables":{"x":null}} 经 Jackson 反序列化就能得到 null 值 —— 那会一路逃成
            // HTTP 500。这里显式拒收，转成 400。检查必须在 copyOf 之前做。
            for (Map.Entry<String, CalcNumber> entry : variables.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) {
                    throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                            "变量名与变量值都不能为 null");
                }
                if (ReservedNames.standard().contains(entry.getKey())) {
                    throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                            "变量名 " + entry.getKey() + " 与函数名或保留常量同名");
                }
            }
            variables = Map.copyOf(variables);
        }
    }

    public static CalculationCommand of(String expression) {
        return new CalculationCommand(expression, null, Map.of());
    }
}
