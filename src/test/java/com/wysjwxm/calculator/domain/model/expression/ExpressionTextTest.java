package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExpressionText 只校验语言层面的不变量（非空、非空白、去首尾空白）。
 * 长度上限是资源保护策略，由 CalculationUseCase 依据 CalculationPolicy 强制
 * —— 见 spec §4.3。
 */
class ExpressionTextTest {

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(new ExpressionText("  1+2  ").value()).isEqualTo("1+2");
    }

    @Test
    void keepsInnerWhitespace() {
        assertThat(new ExpressionText(" 1 + 2 ").value()).isEqualTo("1 + 2");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new ExpressionText(null))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new ExpressionText("   "))
                .isInstanceOf(CalcException.class)
                .extracting(e -> ((CalcException) e).code())
                .isEqualTo(CalcErrorCode.INVALID_REQUEST);
    }

    @Test
    void rejectsEmpty() {
        assertThatThrownBy(() -> new ExpressionText(""))
                .isInstanceOf(CalcException.class);
    }

    @Test
    void veryLongExpressionIsAcceptedByTheValueObject() {
        // 长度不属值对象职责；这里断言它不因此报错
        String longExpression = "1+".repeat(5000) + "1";
        assertThat(new ExpressionText(longExpression).value()).hasSize(longExpression.length());
    }
}
