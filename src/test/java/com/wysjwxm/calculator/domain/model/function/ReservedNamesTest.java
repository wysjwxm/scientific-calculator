package com.wysjwxm.calculator.domain.model.function;

import org.junit.jupiter.api.Test;

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
    void standardIsAStableSingleton() {
        assertThat(ReservedNames.standard()).isSameAs(ReservedNames.standard());
    }
}
