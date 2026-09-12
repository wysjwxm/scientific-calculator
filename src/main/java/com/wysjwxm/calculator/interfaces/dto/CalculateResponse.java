package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.calculation.Calculation;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

/**
 * 求值结果。本期是 MVP：没有历史，因此没有 historyId；没有历史也就没有请求级耗时
 * 的读者，因此没有 elapsedMs。两者随 Phase 2 的历史能力一并补回（spec §7.3 的
 * 完整形态见设计文档）。
 *
 * <p>angleUnit 为 null 时**照常序列化成 JSON null**，不省略字段 —— spec §7.3 的措辞
 * 是「其余记录该字段为 null」。
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
