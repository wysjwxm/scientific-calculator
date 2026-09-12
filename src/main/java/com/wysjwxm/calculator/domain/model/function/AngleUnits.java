package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;

/**
 * 角度单位换算。仅三角相关函数使用。
 */
final class AngleUnits {

    private AngleUnits() {
    }

    static double toRadians(double x, AngleUnit unit) {
        return unit == AngleUnit.DEGREE ? Math.toRadians(x) : x;
    }

    static double fromRadians(double x, AngleUnit unit) {
        return unit == AngleUnit.DEGREE ? Math.toDegrees(x) : x;
    }
}
