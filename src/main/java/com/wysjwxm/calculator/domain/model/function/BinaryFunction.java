package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.List;
import java.util.function.DoubleBinaryOperator;

/**
 * 5 个二元函数 —— 只收录无法用中缀运算符表达的运算（spec §6.5）。
 * 幂与取余已有 ^ 与 % 两种中缀写法，因此不在此注册。
 */
public enum BinaryFunction implements MathFunction {

    HYPOT("hypot", false, Math::hypot),
    MAX("max", false, Math::max),
    MIN("min", false, Math::min),
    ATAN2("atan2", true, Math::atan2),
    LOG("log", false, (x, base) -> Math.log(x) / Math.log(base));

    private final String functionName;
    private final boolean angleSensitive;
    private final DoubleBinaryOperator implementation;

    BinaryFunction(String functionName, boolean angleSensitive,
                   DoubleBinaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.implementation = implementation;
    }

    @Override
    public String functionName() {
        return functionName;
    }

    @Override
    public int arity() {
        return 2;
    }

    @Override
    public boolean angleSensitive() {
        return angleSensitive;
    }

    @Override
    public CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers) {
        if (args.size() != 2) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    functionName + " 需要 2 个参数，实际收到 " + args.size() + " 个");
        }
        double first = args.get(0).toDouble();
        double second = args.get(1).toDouble();
        checkDomain(first, second);

        double raw;
        if (this == ATAN2) {
            // 参数顺序是 atan2(y, x)：args[0] 是 y（纵坐标）、args[1] 是 x（横坐标），
            // 即 Math.atan2(y, x)。两个入参本身无量纲，不参与角度换算；换算的是
            // 返回值 —— 它以弧度为单位，DEGREE 模式下要转成度。
            double y = first;
            double x = second;
            raw = AngleUnits.fromRadians(implementation.applyAsDouble(y, x), angleUnit);
        } else {
            raw = implementation.applyAsDouble(first, second);
        }
        Numbers.requireFinite(raw, functionName);
        return numbers.floating(raw);
    }

    private void checkDomain(double first, double second) {
        if (this == LOG) {
            // log(真数, 底数)
            double real = first;
            double base = second;
            if (real <= 0) {
                throw CalcException.of(CalcErrorCode.DOMAIN_ERROR, "log 的真数必须为正，实际为 " + real);
            }
            if (base <= 0 || base == 1) {
                throw CalcException.of(CalcErrorCode.DOMAIN_ERROR,
                        "log 的底数必须为正且不等于 1，实际为 " + base);
            }
        }
    }
}
