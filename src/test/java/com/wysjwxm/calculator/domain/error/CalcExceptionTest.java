package com.wysjwxm.calculator.domain.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CalcExceptionTest {

    @Test
    void ofCarriesCodeAndMessageAndNullPosition() {
        CalcException ex = CalcException.of(CalcErrorCode.DIVISION_BY_ZERO, "除数不能为零");
        assertThat(ex.code()).isEqualTo(CalcErrorCode.DIVISION_BY_ZERO);
        assertThat(ex.getMessage()).isEqualTo("除数不能为零");
        assertThat(ex.position()).isNull();
    }

    @Test
    void atCarriesPosition() {
        CalcException ex = CalcException.at(CalcErrorCode.PARSE_ERROR, "缺少右括号", 7);
        assertThat(ex.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
        assertThat(ex.position()).isEqualTo(7);
    }

    @Test
    void errorCodeVocabularyIsExactlyTwelve() {
        assertThat(CalcErrorCode.values()).hasSize(12);
    }
}
