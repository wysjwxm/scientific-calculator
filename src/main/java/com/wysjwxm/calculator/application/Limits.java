package com.wysjwxm.calculator.application;

/** 本服务声明的能力边界（数值范围与容量），全部来自代码里的真实常量，不是另写的数字。 */
public record Limits(int maxExpressionLength, int divisionPrecision,
                     int maxExactPowerDigits, int maxDecimalScaleMagnitude) {
}
