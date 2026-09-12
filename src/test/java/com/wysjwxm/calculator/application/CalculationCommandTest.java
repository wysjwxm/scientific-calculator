package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculationCommandTest {

    private static DecimalNumber one() {
        return new DecimalNumber(BigDecimal.ONE);
    }

    private static CalcException reject(String name) {
        return (CalcException) org.assertj.core.api.Assertions
                .catchThrowable(() -> new CalculationCommand("x", null, Map.of(name, one())));
    }

    @Test
    void rejectsVariableNamedAfterABuiltinConstant() {
        assertThat(reject("pi").code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
        assertThat(reject("pi").getMessage()).contains("pi");
        assertThat(reject("e").code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsVariableNamedAfterAFunction() {
        assertThat(reject("sin").code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
        assertThat(reject("log").code()).isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void acceptsAnOrdinaryVariableName() {
        assertThatCode(() -> new CalculationCommand("x+1", null, Map.of("x", one())))
                .doesNotThrowAnyException();
        // 大小写敏感：PI 不是 pi，应当放行
        assertThatCode(() -> new CalculationCommand("PI+1", null, Map.of("PI", one())))
                .doesNotThrowAnyException();
    }

    @Test
    void stillRejectsNullVariableValue() {
        assertThatThrownBy(() -> new CalculationCommand("x", null, java.util.Collections.singletonMap("x", null)))
                .isInstanceOf(CalcException.class);
    }
}
