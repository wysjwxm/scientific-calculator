package com.wysjwxm.calculator.domain.model.function;

import org.junit.jupiter.api.Test;

import java.util.Spliterator;

import static org.assertj.core.api.Assertions.assertThat;

class ReservedNamesTest {

    @Test
    void containsAllFunctionNamesAndConstants() {
        ReservedNames reserved = ReservedNames.standard();
        assertThat(reserved.contains("sin")).isTrue();
        assertThat(reserved.contains("sqrt")).isTrue();
        assertThat(reserved.contains("hypot")).isTrue();
        assertThat(reserved.contains("pi")).isTrue();
        assertThat(reserved.contains("e")).isTrue();
    }

    @Test
    void doesNotContainOrdinaryNames() {
        assertThat(ReservedNames.standard().contains("x")).isFalse();
        assertThat(ReservedNames.standard().contains("x_1")).isFalse();
        assertThat(ReservedNames.standard().contains("foo")).isFalse();
    }

    @Test
    void doesNotAdvertiseRedundantFunctionSpellings() {
        // 有中缀/后缀写法的运算不注册函数形式（spec §12 D3）
        ReservedNames reserved = ReservedNames.standard();
        assertThat(reserved.contains("pow")).isFalse();
        assertThat(reserved.contains("mod")).isFalse();
        assertThat(reserved.contains("fact")).isFalse();
    }

    @Test
    void sizeIsTwentyEightFunctionsPlusTwoConstants() {
        assertThat(ReservedNames.standard().values()).hasSize(23 + 5 + 2);
    }

    @Test
    void valuesPreserveTheDeclaredOrder() {
        // 同 MathematicalConstant.names()：顺序是契约。values() 会喂给能力清单，
        // 因此必须是一元函数 → 二元函数 → 常量 的稳定顺序，不能随散列布局漂移。
        // hasSize 与 containsExactlyInAnyOrder 都不看顺序，抓不住 Set.copyOf 这类改动。
        assertThat(ReservedNames.standard().values()).containsExactly(
                "sin", "cos", "tan", "asin", "acos", "atan",
                "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
                "sqrt", "cbrt", "abs", "exp", "ln", "log10", "log2",
                "floor", "ceil", "round", "sign",
                "hypot", "max", "min", "atan2", "log",
                "pi", "e");
        // 两条断言是配对的，缺一不可（同款说明见 MathematicalConstantTest）：
        // `containsExactly` 抓「顺序确定但排错」的变异（如换成 TreeSet —— 它**是** ORDERED，
        // 却会排成字典序）；`ORDERED` 抓「顺序随机」的变异（如 Set.copyOf，可能碰巧排对）。
        assertThat(ReservedNames.standard().values().spliterator()
                .hasCharacteristics(Spliterator.ORDERED)).isTrue();
    }

    @Test
    void standardIsAStableSingleton() {
        assertThat(ReservedNames.standard()).isSameAs(ReservedNames.standard());
    }
}
