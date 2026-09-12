package com.wysjwxm.calculator.domain;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.FloatingNumber;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 内置保留常量。
 *
 * <p>这些名字属于语言的一部分，不是可被覆盖的缺省值 —— 用户变量不得使用它们
 * （见 spec §6.4）。若允许 pi = 3，则 sin(pi) 的含义会随写入操作静默改变。
 *
 * <p>用枚举而非 Map：常量集是编译期固定的，枚举让这一点显式，也让
 * {@link #names()} 可以静态求值，无需任何运行时容器。
 */
public enum MathematicalConstant {

    PI("pi", Math.PI),
    E("e", Math.E);

    private final String symbol;
    private final double rawValue;

    MathematicalConstant(String symbol, double rawValue) {
        this.symbol = symbol;
        this.rawValue = rawValue;
    }

    /** 语言中的常量名（小写）。注意不能用 {@code name()} —— 那会返回枚举标识符 "PI"。 */
    public String symbol() {
        return symbol;
    }

    public CalcNumber value() {
        return new FloatingNumber(rawValue);
    }

    public static Optional<CalcNumber> lookup(String name) {
        for (MathematicalConstant constant : values()) {
            if (constant.symbol.equals(name)) {
                return Optional.of(constant.value());
            }
        }
        return Optional.empty();
    }

    /**
     * 常量名集合，**保持枚举声明顺序**。
     *
     * <p>刻意不用 {@code Set.copyOf}：{@code java.util.ImmutableCollections} 会随机化
     * 元素顺序，同一份代码在不同 JVM 运行中迭代出的顺序可能不同。这个集合会喂给
     * Phase 2 的 /functions 能力清单，顺序漂移意味着清单每次启动都不一样，测试也会
     * 随机变红。用 LinkedHashSet 固定顺序，再包一层不可修改视图防止外部改动。
     */
    public static Set<String> names() {
        Set<String> names = new LinkedHashSet<>();
        for (MathematicalConstant constant : values()) {
            names.add(constant.symbol);
        }
        return Collections.unmodifiableSet(names);
    }
}
