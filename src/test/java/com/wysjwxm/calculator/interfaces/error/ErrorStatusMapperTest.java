package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码 → HTTP 状态的映射必须覆盖全部枚举值。
 *
 * <p>这条测试是防漂移的守门人：映射表与枚举分处两个包，若无人看守，
 * 新增错误码会静默退化成 500 —— 而不是在编译期或测试期暴露。
 */
class ErrorStatusMapperTest {

    @Test
    void everyErrorCodeMapsToAStatus() {
        for (CalcErrorCode code : CalcErrorCode.values()) {
            assertThat(ErrorStatusMapper.toStatus(code))
                    .as("错误码 %s 缺少 HTTP 状态映射", code)
                    .isNotNull();
        }
    }

    @Test
    void parseErrorsAreBadRequest() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.PARSE_ERROR)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.INVALID_REQUEST)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.UNKNOWN_FUNCTION)).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void notFoundCodesMapTo404() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.VARIABLE_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.HISTORY_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.NO_HANDLER)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void semanticErrorsMapTo422() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.UNKNOWN_VARIABLE))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.DIVISION_BY_ZERO))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.DOMAIN_ERROR))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.NON_FINITE_RESULT))
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void methodNotAllowedMapsTo405() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.METHOD_NOT_ALLOWED))
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void internalErrorMapsTo500() {
        assertThat(ErrorStatusMapper.toStatus(CalcErrorCode.INTERNAL_ERROR))
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
