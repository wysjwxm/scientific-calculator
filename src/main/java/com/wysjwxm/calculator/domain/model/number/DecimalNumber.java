package com.wysjwxm.calculator.domain.model.number;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

import java.math.BigDecimal;
import java.util.Objects;

public record DecimalNumber(BigDecimal value) implements CalcNumber {

    /** scale 的绝对值上界。BigDecimal 的 scale 合法区间是整个 int（±21 亿），但两个
     *  scale 相差 N 的数做加减/取余，要把较小的一方补齐成 10^N 位的大整数 —— N 上亿时
     *  BigDecimal 自己抛 ArithmeticException（"BigInteger would overflow supported
     *  range"）；乘法的结果 scale 越界则抛 Overflow / Underflow。这些异常会一路逃成
     *  HTTP 500。界取 10 万，与 Numbers 的精确幂位数预算同量级：1E±100000 之外没有
     *  真实用途，界内最坏的对齐开销是 20 万位，实测约 7 毫秒（见报告）。
     *  判据写成两次比较而不是 Math.abs —— Math.abs(Integer.MIN_VALUE) 是负数，
     *  用绝对值的写法会漏掉 scale = Integer.MIN_VALUE 这唯一一个值。
     *  已作为能力清单的 limits 对外发布，故为 public（值与语义不变）。 */
    public static final int MAX_SCALE_MAGNITUDE = 100_000;

    public DecimalNumber {
        Objects.requireNonNull(value, "value");
        int scale = value.scale();
        if (scale > MAX_SCALE_MAGNITUDE || scale < -MAX_SCALE_MAGNITUDE) {
            throw CalcException.of(CalcErrorCode.NON_FINITE_RESULT,
                    "数值超出可表示范围：scale 为 " + scale
                            + "，允许的绝对值为 " + MAX_SCALE_MAGNITUDE);
        }
    }

    @Override
    public double toDouble() {
        return value.doubleValue();
    }

    @Override
    public BigDecimal toDecimal() {
        return value;
    }

    @Override
    public boolean isExact() {
        return true;
    }
}
