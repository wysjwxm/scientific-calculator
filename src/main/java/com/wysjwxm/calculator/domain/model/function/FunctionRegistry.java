package com.wysjwxm.calculator.domain.model.function;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 函数名 → 实现的查表。
 *
 * <p>底表用 {@link LinkedHashMap} 而非 {@code HashMap}：{@code unaryNames()} 与
 * {@code binaryNames()} 会喂给 /functions 能力清单，顺序必须跟着枚举声明走，
 * 不能随散列布局漂移。
 */
public final class FunctionRegistry {

    private final Map<String, MathFunction> byName = new LinkedHashMap<>();

    public FunctionRegistry() {
        for (UnaryFunction f : UnaryFunction.values()) {
            byName.put(f.functionName(), f);
        }
        for (BinaryFunction f : BinaryFunction.values()) {
            byName.put(f.functionName(), f);
        }
    }

    public Optional<MathFunction> find(String name) {
        return Optional.ofNullable(byName.get(name));
    }

    public List<String> unaryNames() {
        return Arrays.stream(UnaryFunction.values()).map(UnaryFunction::functionName).toList();
    }

    public List<String> binaryNames() {
        return Arrays.stream(BinaryFunction.values()).map(BinaryFunction::functionName).toList();
    }
}
