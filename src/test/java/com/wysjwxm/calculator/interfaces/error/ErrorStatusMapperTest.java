package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 错误码 → HTTP 状态的映射必须覆盖全部枚举值。
 *
 * <p>防漂移的第一道网在**编译期**：{@link ErrorStatusMapper#toStatus} 是对枚举的穷尽
 * switch 表达式且没有 default 分支，新增 {@link CalcErrorCode} 常量会让它编不过 ——
 * 不会静默退化成 500。
 *
 * <p>下面这组测试补的是编译期管不到的另一半 —— **映射的选择**：12 个错误码各映到哪个
 * 状态（400 / 404 / 405 / 422 / 500）是设计决定，编译器只保证「有映射」，保证不了
 * 「映射对」。其中 {@code everyErrorCodeMapsToAStatus} 在当前的穷尽 switch 实现下
 * 不可能失败，它守的是「将来有人把 switch 换成 Map 查表」那种形状变化；
 * 真正带防变异能力的是下面五条钉住具体状态的断言。
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
