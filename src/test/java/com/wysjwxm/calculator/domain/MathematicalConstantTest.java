package com.wysjwxm.calculator.domain;

import org.junit.jupiter.api.Test;

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
        // 顺序是契约而非巧合（明文裁定 R6）：这个集合会喂给 Phase 2 的能力清单，
        // 用 Set.copyOf 会让迭代顺序随 JVM 运行漂移（ImmutableCollections 用随机 SALT），
        // 清单每次启动都不一样。注意 isEqualToInAnyOrder/hasSize 都不看顺序，抓不住它 ——
        // 这条断言的意义不是检查这两个名字，而是把「顺序是契约」这个决定变成可执行的。
        assertThat(MathematicalConstant.names()).containsExactly("pi", "e");

        // 上面那条 containsExactly 单独还不够：Set.copyOf 对两个元素的小集合走
        // ImmutableCollections$Set12，而它的迭代顺序由启动期的随机 SALT 决定 —— 实测
        // 多数运行里它**碰巧**就是插入顺序，于是断言在那些运行里空转。这里直接钉住
        // 实现约束：返回集合不能来自顺序会漂移的 ImmutableCollections。
        assertThat(MathematicalConstant.names().getClass().getName())
                .doesNotContain("ImmutableCollections");
    }

    @Test
    void symbolIsTheLowerCaseNameNotTheEnumIdentifier() {
        // Enum.name() 会返回 "PI"；语言里的常量名是小写 "pi"
        assertThat(MathematicalConstant.PI.symbol()).isEqualTo("pi");
        assertThat(MathematicalConstant.E.symbol()).isEqualTo("e");
    }
}
