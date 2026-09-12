package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

/**
 * 23 个一元函数。表驱动而非 23 个类 —— 每个函数的差异只有五件事：
 * 语言名、是否受角度影响、定义域约束、面向调用方的一句话说明、double 实现。
 *
 * <p>定义域违规一律抛 DOMAIN_ERROR，不静默返回 NaN。
 *
 * <p>{@code description} 住在枚举里而不是接口层的清单表里：说明与函数是同一份事实的
 * 两面，写两处必然漂移（spec §7.2 / D8 对算子清单的同一要求）。
 */
public enum UnaryFunction implements MathFunction {

    // 三角函数（角度敏感）
    SIN("sin", true, Domain.ANY,
            "正弦。入参按 angleUnit 解释为角度或弧度，返回比值", Math::sin),
    COS("cos", true, Domain.ANY,
            "余弦。入参按 angleUnit 解释为角度或弧度，返回比值", Math::cos),
    TAN("tan", true, Domain.ANY,
            "正切。入参按 angleUnit 解释为角度或弧度，返回比值", Math::tan),
    ASIN("asin", true, Domain.UNIT_INTERVAL,
            "反正弦。入参是比值不换算，返回值按 angleUnit 换算为角度或弧度", Math::asin),
    ACOS("acos", true, Domain.UNIT_INTERVAL,
            "反余弦。入参是比值不换算，返回值按 angleUnit 换算为角度或弧度", Math::acos),
    ATAN("atan", true, Domain.ANY,
            "反正切。入参是比值不换算，返回值按 angleUnit 换算为角度或弧度", Math::atan),

    // 双曲函数（角度无关）
    SINH("sinh", false, Domain.ANY, "双曲正弦。与角度单位无关", Math::sinh),
    COSH("cosh", false, Domain.ANY, "双曲余弦。与角度单位无关", Math::cosh),
    TANH("tanh", false, Domain.ANY, "双曲正切。与角度单位无关", Math::tanh),
    // Java 17 的 java.lang.Math 只有 sinh/cosh/tanh，三个反双曲函数是 JDK 20 才加入的，
    // 因此这里按数学恒等式自己算（不能写 Math::asinh，本项目在 Java 17 上编译不过）。
    // asinh 用带符号的形式而非 log(x + sqrt(x²+1))：后者在 x 为大负数时是两个大数相减，
    // 有效位被抵消光，会得到 0 甚至 NaN。
    ASINH("asinh", false, Domain.ANY,
            "反双曲正弦。用带符号的恒等式计算，避免大负数下的相减抵消",
            x -> Math.signum(x) * Math.log(Math.abs(x) + Math.sqrt(x * x + 1))),
    ACOSH("acosh", false, Domain.AT_LEAST_ONE, "反双曲余弦",
            x -> Math.log(x + Math.sqrt(x * x - 1))),
    ATANH("atanh", false, Domain.OPEN_UNIT_INTERVAL, "反双曲正切",
            x -> 0.5 * Math.log((1 + x) / (1 - x))),

    // 幂与根
    SQRT("sqrt", false, Domain.NON_NEGATIVE, "平方根", Math::sqrt),
    CBRT("cbrt", false, Domain.ANY, "立方根，负数亦可（结果与入参同号）", Math::cbrt),

    // 取绝对值、指数与对数
    ABS("abs", false, Domain.ANY, "绝对值", Math::abs),
    EXP("exp", false, Domain.ANY, "自然指数 e 的 x 次幂", Math::exp),
    LN("ln", false, Domain.POSITIVE, "自然对数", Math::log),
    LOG10("log10", false, Domain.POSITIVE, "常用对数，以 10 为底", Math::log10),
    LOG2("log2", false, Domain.POSITIVE, "以 2 为底的对数", x -> Math.log(x) / Math.log(2)),

    // 取整与符号
    FLOOR("floor", false, Domain.ANY, "向下取整到最近的整数", Math::floor),
    CEIL("ceil", false, Domain.ANY, "向上取整到最近的整数", Math::ceil),
    ROUND("round", false, Domain.LONG_RANGE, "四舍五入到最近的整数，.5 向上取整",
            x -> (double) Math.round(x)),
    SIGN("sign", false, Domain.ANY, "符号函数，返回 -1、0 或 1", Math::signum);

    /**
     * 对函数参数 x 的定义域约束。
     *
     * <p>{@code description} 是给调用方读的中文边界说明，只用于能力清单展示；
     * 真正的强制点是 {@link UnaryFunction#checkDomain}，两者不得互相替代。
     *
     * <p>本枚举对外可见：应用层的 {@code CapabilityQuery} 要读 {@link #description()}
     * 生成清单。它是只读的边界说明，不暴露任何强制逻辑。
     */
    public enum Domain {
        ANY("任意实数"),
        NON_NEGATIVE("非负实数（x ≥ 0）"),
        POSITIVE("正实数（x > 0）"),
        UNIT_INTERVAL("闭区间 [-1, 1]"),        // asin / acos
        AT_LEAST_ONE("x ≥ 1"),                  // acosh
        OPEN_UNIT_INTERVAL("开区间 (-1, 1)"),   // atanh
        LONG_RANGE("|x| < 2^63，超出则舍入会饱和到长整型上界");  // round

        private final String description;

        Domain(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }

    private final String functionName;
    private final boolean angleSensitive;
    private final Domain domain;
    private final String description;
    private final DoubleUnaryOperator implementation;

    UnaryFunction(String functionName, boolean angleSensitive, Domain domain,
                  String description, DoubleUnaryOperator implementation) {
        this.functionName = functionName;
        this.angleSensitive = angleSensitive;
        this.domain = domain;
        this.description = description;
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
    public String description() {
        return description;
    }

    /** 参数 x 的定义域约束，供能力清单展示边界。强制点仍是 {@link #checkDomain}。 */
    public Domain domain() {
        return domain;
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
