package com.wysjwxm.calculator.domain.model.number;

import java.math.BigDecimal;

/**
 * 计算器数值。两条路径显式分开：
 * <ul>
 *   <li>{@link DecimalNumber} —— 四则运算路径，精确</li>
 *   <li>{@link FloatingNumber} —— 超越函数路径，存在浮点误差</li>
 * </ul>
 *
 * <p>保底不变量：结果只要落在 DecimalNumber 就永远精确；一旦沾了 FloatingNumber
 * 即存在浮点误差。此不变量在测试中固定。
 */
public sealed interface CalcNumber permits DecimalNumber, FloatingNumber {

    double toDouble();

    BigDecimal toDecimal();

    /** 是否落在精确路径上。 */
    boolean isExact();
}
