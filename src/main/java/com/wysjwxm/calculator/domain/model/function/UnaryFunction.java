package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 23 个一元函数。表驱动而非 23 个类 —— 每个函数的差异只有四件事：
 * 语言名、是否受角度影响、定义域约束、double 实现。
 *
 * <p>定义域违规一律抛 DOMAIN_ERROR，不静默返回 NaN。
 */
public enum UnaryFunction implements MathFunction {

    // 三角函数（角度敏感）
    SIN("sin", true, Domain.ANY, Math::sin),
    COS("cos", true, Domain.ANY, Math::cos),
    TAN("tan", true, Domain.ANY, Math::tan),
    ASIN("asin", true, Domain.UNIT_INTERVAL, Math::asin),
    ACOS("acos", true, Domain.UNIT_INTERVAL, Math::acos),
    ATAN("atan", true, Domain.ANY, Math::atan),

    // 双曲函数（角度无关）
    SINH("sinh", false, Domain.ANY, Math::sinh),
    COSH("cosh", false, Domain.ANY, Math::cosh),
    TANH("tanh", false, Domain.ANY, Math::tanh),
    // Java 17 的 java.lang.Math 只有 sinh/cosh/tanh，三个反双曲函数是 JDK 20 才加入的，
    // 因此这里按数学恒等式自己算（不能写 Math::asinh，本项目在 Java 17 上编译不过）。
    // asinh 用带符号的形式而非 log(x + sqrt(x²+1))：后者在 x 为大负数时是两个大数相减，
    // 有效位被抵消光，会得到 0 甚至 NaN。
    ASINH("asinh", false, Domain.ANY,
            x -> Math.signum(x) * Math.log(Math.abs(x) + Math.sqrt(x * x + 1))),
    ACOSH("acosh", false, Domain.AT_LEAST_ONE, x -> Math.log(x + Math.sqrt(x * x - 1))),
    ATANH("atanh", false, Domain.OPEN_UNIT_INTERVAL, x -> 0.5 * Math.log((1 + x) / (1 - x))),

    // 幂与根
    SQRT("sqrt", false, Domain.NON_NEGATIVE, Math::sqrt),
    CBRT("cbrt", false, Domain.ANY, Math::cbrt),

    // 取绝对值、指数与对数
    ABS("abs", false, Domain.ANY, Math::abs),
    EXP("exp", false, Domain.ANY, Math::exp),
    LN("ln", false, Domain.POSITIVE, Math::log),
    LOG10("log10", false, Domain.POSITIVE, Math::log10),
    LOG2("log2", false, Domain.POSITIVE, x -> Math.log(x) / Math.log(2)),

    // 取整与符号
    FLOOR("floor", false, Domain.ANY, Math::floor),
    CEIL("ceil", false, Domain.ANY, Math::ceil),
    ROUND("round", false, Domain.LONG_RANGE, x -> (double) Math.round(x)),
    SIGN("sign", false, Domain.ANY, Math::signum);

    /** 对函数参数 x 的定义域约束。 */
    enum Domain {
        ANY,
        NON_NEGATIVE,
        POSITIVE,
        UNIT_INTERVAL,        // asin / acos： -1 <= x <= 1
        AT_LEAST_ONE,         // acosh：      x >= 1
        OPEN_UNIT_INTERVAL,   // atanh：      -1 < x < 1
        LONG_RANGE            // round：      |x| < 2^63，超出则 Math.round 会饱和到 Long.MAX_VALUE
    }

    private final String functionName;
    private final boolean angleSensitive;
    private final Domain domain;
    private final DoubleUnaryOperator implementation;

    UnaryFunction(String functionName, boolean angleSensitive, Domain domain,
                  DoubleUnaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.domain = domain;
        this.implementation = implementation;
    }

    @Override
    public String functionName() {
        return functionName;
    }

    @Override
    public int arity() {
        return 1;
    }

    @Override
    public boolean angleSensitive() {
        return angleSensitive;
    }

    @Override
    public CalcNumber apply(List<CalcNumber> args, AngleUnit angleUnit, Numbers numbers) {
        if (args.size() != 1) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST,
                    functionName + " 需要 1 个参数，实际收到 " + args.size() + " 个");
        }
        double x = args.get(0).toDouble();
        checkDomain(x);

        double raw;
        if (isInverseTrigonometric()) {
            // 反三角：入参是比值（无量纲），不参与换算；**返回值**才是角度，按单位换算。
            raw = AngleUnits.fromRadians(implementation.applyAsDouble(x), angleUnit);
        } else if (angleSensitive) {
            // 正三角：反过来 —— 入参是角度要换算，返回值是比值。
            raw = implementation.applyAsDouble(AngleUnits.toRadians(x, angleUnit));
        } else {
            raw = implementation.applyAsDouble(x);
        }
        Numbers.requireFinite(raw, functionName);
        return numbers.floating(raw);
    }

    private boolean isInverseTrigonometric() {
        return this == ASIN || this == ACOS || this == ATAN;
    }

    private void checkDomain(double x) {
        boolean ok = switch (domain) {
            case ANY -> true;
            case NON_NEGATIVE -> x >= 0;
            case POSITIVE -> x > 0;
            case UNIT_INTERVAL -> x >= -1 && x <= 1;
            case AT_LEAST_ONE -> x >= 1;
            case OPEN_UNIT_INTERVAL -> x > -1 && x < 1;
            case LONG_RANGE -> Math.abs(x) < 9.223372036854776E18;
        };
        if (!ok) {
            throw CalcException.of(CalcErrorCode.DOMAIN_ERROR,
                    functionName + " 的定义域不允许 " + x);
        }
    }
}
