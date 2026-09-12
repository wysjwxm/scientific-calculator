package com.wysjwxm.calculator.domain.model.calculation;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;

/**
 * 一次成功求值的结果：表达式原文、计算结果、以及该次求值实际生效的角度单位。
 *
 * <p>没有 id / 耗时 / 时间戳 —— 这三个字段的**唯一读者**是历史记录，而历史上
 * 属 Phase 2。在还没有历史聚合根的时候，id 没有任何正确值可填（填 0 是在数据里
 * 说谎），耗时与时间戳同理只是没有读者的元数据。Phase 2 建历史时加回来即可，
 * 消费方 {@code CalculateResponse} 只读下面这三个分量，字段增减不影响它。
 *
 * @param angleUnit 表达式用到了角度敏感函数时是实际生效的单位，否则为 null
 */
public record Calculation(ExpressionText expression, CalcNumber result, AngleUnit angleUnit) {
}
