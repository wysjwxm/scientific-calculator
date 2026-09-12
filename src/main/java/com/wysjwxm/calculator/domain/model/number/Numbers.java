package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 算术运算与类型提升的领域服务。所有提升规则集中在此，不散落到求值器里。
 *
 * <p>实例类而非静态工具类，因为除法精度来自配置 —— 把 MathContext 作为
 * 构造参数注入，测试才能固定精度。
 */
public final class Numbers {

    /** 阶乘上界：171! 超出 double 范围。 */
    private static final int FACTORIAL_LIMIT = 170;

    /** 精确幂的指数上界：更大的指数会让 BigDecimal.pow 产生数亿位的中间结果，
     *  而 double 无论如何都会溢出，直接走浮点路径由 requireFinite 兜底。 */
    private static final int MAX_EXACT_EXPONENT = 10_000;

    /** double 有约 15~17 位有效数字：按 15 位有效数字规整，抹掉运算末位的噪声
     *  （如 sin(30°) 得到的 0.49999999999999994 → 0.5），同时不损失有效精度。 */
    private static final MathContext NORMALIZE_CONTEXT = new MathContext(15);

    private final MathContext divisionContext;

    public Numbers(int divisionPrecision) {
        if (divisionPrecision < 1) {
            throw new IllegalArgumentException("divisionPrecision 必须为正数: " + divisionPrecision);
        }
        this.divisionContext = new MathContext(divisionPrecision, RoundingMode.HALF_EVEN);
    }

    public CalcNumber of(long v) {
        return new DecimalNumber(BigDecimal.valueOf(v));
    }

    public CalcNumber of(BigDecimal v) {
        return new DecimalNumber(v);
    }

    public CalcNumber floating(double v) {
        return new FloatingNumber(v);
    }

    public CalcNumber add(CalcNumber a, CalcNumber b) {
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().add(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() + b.toDouble(), "加法");
    }

    public CalcNumber subtract(CalcNumber a, CalcNumber b) {
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().subtract(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() - b.toDouble(), "减法");
    }

    public CalcNumber multiply(CalcNumber a, CalcNumber b) {
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().multiply(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() * b.toDouble(), "乘法");
    }

    public CalcNumber divide(CalcNumber a, CalcNumber b) {
        if (isZero(b)) {
            throw CalcException.of(CalcErrorCode.DIVISION_BY_ZERO, "除数不能为零");
        }
        if (bothExact(a, b)) {
            BigDecimal dividend = a.toDecimal();
            BigDecimal divisor = b.toDecimal();
            try {
                // 能整除时给出精确结果，除不尽时才按配置精度截断
                return new DecimalNumber(dividend.divide(divisor));
            } catch (ArithmeticException nonTerminating) {
                return new DecimalNumber(dividend.divide(divisor, divisionContext));
            }
        }
        return floatingFinite(a.toDouble() / b.toDouble(), "除法");
    }

    public CalcNumber modulo(CalcNumber a, CalcNumber b) {
        if (isZero(b)) {
            throw CalcException.of(CalcErrorCode.DIVISION_BY_ZERO, "模运算的除数不能为零");
        }
        if (bothExact(a, b)) {
            return new DecimalNumber(a.toDecimal().remainder(b.toDecimal()));
        }
        return floatingFinite(a.toDouble() % b.toDouble(), "取余");
    }

    public CalcNumber power(CalcNumber base, CalcNumber exponent) {
        if (base.isExact() && exponent.isExact()) {
            BigDecimal exp = exponent.toDecimal();
            // 仅非负整数指数走精确路径；负指数与分数指数降级到 double
            if (exp.stripTrailingZeros().scale() <= 0 && exp.signum() >= 0) {
                if (exp.compareTo(BigDecimal.valueOf(MAX_EXACT_EXPONENT)) <= 0) {
                    return new DecimalNumber(base.toDecimal().pow(exp.intValueExact()));
                }
                return floatingFinite(Math.pow(base.toDouble(), exp.doubleValue()), "幂运算");
            }
        }
        return floatingFinite(Math.pow(base.toDouble(), exponent.toDouble()), "幂运算");
    }

    public CalcNumber negate(CalcNumber a) {
        if (a.isExact()) {
            return new DecimalNumber(a.toDecimal().negate());
        }
        return floatingFinite(-a.toDouble(), "取负");
    }

    public CalcNumber factorial(CalcNumber a) {
        BigDecimal n = a.toDecimal();
        if (n.stripTrailingZeros().scale() > 0 || n.signum() < 0) {
            throw CalcException.of(CalcErrorCode.DOMAIN_ERROR,
                    "阶乘只接受非负整数，实际为 " + n.toPlainString());
        }
        int value = n.intValueExact();
        if (value > FACTORIAL_LIMIT) {
            throw CalcException.of(CalcErrorCode.NON_FINITE_RESULT,
                    "阶乘上界为 " + FACTORIAL_LIMIT + "，实际为 " + value);
        }
        BigDecimal result = BigDecimal.ONE;
        for (int i = 2; i <= value; i++) {
            result = result.multiply(BigDecimal.valueOf(i));
        }
        return new DecimalNumber(result);
    }

    /** 非有限结果一律拒收，不把 Inf/NaN 透出到响应体。 */
    public static void requireFinite(double v, String what) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            throw CalcException.of(CalcErrorCode.NON_FINITE_RESULT, what + "结果超出可表示范围");
        }
    }

    private CalcNumber floatingFinite(double v, String what) {
        // 先判非有限：既拦住 Inf/NaN，也让下面的 BigDecimal.valueOf 不会收到 NaN
        requireFinite(v, what);
        double normalized = BigDecimal.valueOf(v).round(NORMALIZE_CONTEXT).doubleValue();
        // 再判一次：15 位有效数字的进位可能把接近 Double.MAX_VALUE 的值推到 Infinity
        requireFinite(normalized, what);
        return new FloatingNumber(normalized);
    }

    private static boolean bothExact(CalcNumber a, CalcNumber b) {
        return a.isExact() && b.isExact();
    }

    private static boolean isZero(CalcNumber n) {
        return n.toDecimal().signum() == 0;
    }
}
