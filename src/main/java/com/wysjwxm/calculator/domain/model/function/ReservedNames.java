package com.wysjwxm.calculator.domain.model.function;

import com.wysjwxm.calculator.domain.MathematicalConstant;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 保留名集合 = 函数名 ∪ 保留常量名（spec §6.4）。
 *
 * <p>注意它**不依赖 FunctionRegistry 实例**：函数集与常量集都是编译期固定的
 * 枚举，因此集合可以静态求值。这一点是 VariableName 能把校验完全放进构造器
 * 的前提 —— 见 spec §12 D13。
 *
 * <p>集合保持「一元函数 → 二元函数 → 常量」的稳定顺序：{@link #values()} 会被
 * 能力清单读取，顺序若随 JVM 运行漂移，清单就不可复现。刻意不用 {@code Set.copyOf}
 * （{@code ImmutableCollections} 会随机化迭代顺序）。
 */
public record ReservedNames(Set<String> values) {

    private static final ReservedNames STANDARD = new ReservedNames(compute());

    public ReservedNames {
        values = Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    public static ReservedNames standard() {
        return STANDARD;
    }

    public boolean contains(String name) {
        return values.contains(name);
    }

    private static Set<String> compute() {
        Set<String> names = new LinkedHashSet<>();
        for (UnaryFunction function : UnaryFunction.values()) {
            names.add(function.functionName());
        }
        for (BinaryFunction function : BinaryFunction.values()) {
            names.add(function.functionName());
        }
        names.addAll(MathematicalConstant.names());
        return names;
    }
}
