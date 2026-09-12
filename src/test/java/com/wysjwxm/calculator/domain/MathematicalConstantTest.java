package com.wysjwxm.calculator.domain;

import org.junit.jupiter.api.Test;

import java.util.Spliterator;

import static org.assertj.core.api.Assertions.assertThat;

class MathematicalConstantTest {

    @Test
    void resolvesPiAndE() {
        assertThat(MathematicalConstant.lookup("pi").orElseThrow().toDouble())
                .isCloseTo(Math.PI, org.assertj.core.data.Offset.offset(1e-15));
        assertThat(MathematicalConstant.lookup("e").orElseThrow().toDouble())
                .isCloseTo(Math.E, org.assertj.core.data.Offset.offset(1e-15));
    }

    @Test
    void unknownNameIsNotFound() {
        assertThat(MathematicalConstant.lookup("x")).isEmpty();
    }

    @Test
    void namesAreExactlyPiAndE() {
        assertThat(MathematicalConstant.names()).containsExactlyInAnyOrder("pi", "e");
    }

    @Test
    void namesPreserveEnumDeclarationOrder() {
        // 顺序是契约（R6）：`Set.copyOf` 的迭代顺序由启动期随机 SALT 决定，
        // 会让 Phase 2 的能力清单每次启动都不一样。
        assertThat(MathematicalConstant.names()).containsExactly("pi", "e");
        // 上面那条 `containsExactly` 单独会空转：对 2 元素集合，`Set.copyOf` 走
        // ImmutableCollections$Set12，顺序由随机 SALT 决定，多数运行下碰巧等于
        // 声明顺序（实测 8 次独立 JVM：5 次 [pi, e]、3 次 [e, pi]）。
        // 用 Spliterator.ORDERED 判定：它是「迭代顺序有意义且被保留」的 API 级
        // 表述，对 ImmutableCollections 恒为 false、对 LinkedHashSet 恒为 true，
        // 且不依赖任何 JDK 内部类名。
        assertThat(MathematicalConstant.names().spliterator()
                .hasCharacteristics(Spliterator.ORDERED)).isTrue();
    }

    @Test
    void symbolIsTheLowerCaseNameNotTheEnumIdentifier() {
        // Enum.name() 会返回 "PI"；语言里的常量名是小写 "pi"
        assertThat(MathematicalConstant.PI.symbol()).isEqualTo("pi");
        assertThat(MathematicalConstant.E.symbol()).isEqualTo("e");
    }
}
