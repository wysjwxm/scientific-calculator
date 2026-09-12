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
    void symbolIsTheLowerCaseNameNotTheEnumIdentifier() {
        // Enum.name() 会返回 "PI"；语言里的常量名是小写 "pi"
        assertThat(MathematicalConstant.PI.symbol()).isEqualTo("pi");
        assertThat(MathematicalConstant.E.symbol()).isEqualTo("e");
    }
}
