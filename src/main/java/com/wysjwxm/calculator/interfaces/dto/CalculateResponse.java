package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

/**
 * 求值结果。本期是 MVP：没有历史，因此没有 historyId；没有历史也就没有请求级耗时
 * 的读者，因此没有 elapsedMs。两者随 Phase 2 的历史能力一并补回（**spec §7.1 的 200 样例**
 * 是完整形态，见设计文档）。
 *
 * <p>angleUnit 为 null 时**照常序列化成 JSON null**，不省略字段。这是本期的设计选择：
 * **spec 没有规定求值响应的 null 形态**（§7.1 的 200 样例只给出取值非 null 的一种）。
 * 选择保留字段而非省略，是因为「字段在场且为 null」比「字段缺席」更能表达
 * 「本次求值不涉及角度」。spec :420 那句「其余记录该字段为 `null`」是**历史记录**
 * （§7.3）一节对历史条目字段的规定，与这里无关，不要引作依据。
 */
public record CalculateResponse(
        String expression,
        @JsonSerialize(using = CalcNumberSerializer.class) CalcNumber result,
        String resultType,
        AngleUnit angleUnit) {

    public static CalculateResponse from(Calculation calculation) {
        return new CalculateResponse(
                calculation.expression().value(),
                calculation.result(),
                calculation.result().isExact() ? "DECIMAL" : "FLOATING",
                calculation.angleUnit());
    }
}
