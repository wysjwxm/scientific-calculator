package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;

/**
 * 应用层策略：来自配置、但由用例施加的取值。
 *
 * <p>纯 Java record，无任何框架注解 —— 这样 application 层不必 import
 * infrastructure 里的 CalculatorProperties，依赖方向不被配置类破口。
 *
 * @param maxExpressionLength 表达式长度上限。它是资源保护策略而非语言不变量，
 *                            因此不进 ExpressionText 的构造器，在此处强制。
 * @param divisionPrecision   除法（除不尽时）保留的有效位数，交给 {@code Numbers} 使用。
 *                            本 record 只负责携带与校验，取值本身来自配置绑定。
 */
public record CalculationPolicy(AngleUnit defaultAngleUnit, int maxExpressionLength,
                                int divisionPrecision) {

    public CalculationPolicy {
        if (defaultAngleUnit == null) {
            throw new IllegalArgumentException("defaultAngleUnit 不能为空");
        }
        if (maxExpressionLength < 1) {
            throw new IllegalArgumentException("maxExpressionLength 必须为正数: " + maxExpressionLength);
        }
        if (divisionPrecision < 1) {
            throw new IllegalArgumentException("divisionPrecision 必须为正数: " + divisionPrecision);
        }
    }
}
