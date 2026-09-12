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
 *
 * <p>二元函数与一元不同：它们的定义域约束随函数而变（只有 log 有约束），写在
 * {@link #checkDomain} 的代码里，没有可枚举的定义域类型。故这里用
 * {@code domainDescription} 承载**给人读的**边界文案，供能力清单展示。
 * {@link #checkDomain} 仍是唯一的强制点 —— 绝不许改成读这个字段来判定。
 */
public enum BinaryFunction implements MathFunction {

    HYPOT("hypot", false, "任意实数",
            "直角三角形斜边 sqrt(x²+y²)，无中间溢出", Math::hypot),
    MAX("max", false, "任意实数", "两个参数中的较大值", Math::max),
    MIN("min", false, "任意实数", "两个参数中的较小值", Math::min),
    ATAN2("atan2", true, "任意实数",
            "atan2(y, x)：按点 (x, y) 所在象限返回角度。两个入参是比值不换算，返回值按 angleUnit 换算",
            Math::atan2),
    LOG("log", false, "真数 > 0，且底数 > 0 且底数 ≠ 1", "对数 log(真数, 底数)",
            (x, base) -> Math.log(x) / Math.log(base));

    private final String functionName;
    private final boolean angleSensitive;
    private final String domainDescription;
    private final String description;
    private final DoubleBinaryOperator implementation;

    BinaryFunction(String functionName, boolean angleSensitive, String domainDescription,
                   String description, DoubleBinaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.domainDescription = domainDescription;
        this.description = description;
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
    public String description() {
        return description;
    }

    /** 参数的定义域边界，供能力清单展示。强制点仍是 {@link #checkDomain}。 */
    public String domainDescription() {
        return domainDescription;
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
