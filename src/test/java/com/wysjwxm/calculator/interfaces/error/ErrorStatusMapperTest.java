package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;

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
 * 不可能失败，它只在「有人把 switch 换成**裸** Map 查表（{@code map.get(code)} 直接
 * 返回 null）」时才有意义；若换成的查表**自带兜底**（{@code getOrDefault(..., 500)}
 * 或 switch 补 {@code default}），新增错误码会静默落进 500，这条 {@code isNotNull}
 * 断言照样通过 —— 那道口子由 {@code internalErrorIsTheOnlyCodeThatMapsToFiveHundred} 守。
 * 真正带防变异能力的是下面这些钉住具体状态的断言。
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

    @Test
    void internalErrorIsTheOnlyCodeThatMapsToFiveHundred() {
        // 结构性不变量：500 是 INTERNAL_ERROR 专属 —— 也就是「每个错误码的状态都是被
        // 逐码指定的」。这条换个角度钉住映射表：若有人给 toStatus 补 default 分支、或把
        // switch 换成带兜底的查表（getOrDefault(..., 500)），新增的错误码会静默落进 500，
        // 而 everyErrorCodeMapsToAStatus 的 isNotNull 断言不会红 —— 这条会。
        assertThat(Arrays.stream(CalcErrorCode.values())
                .filter(code -> ErrorStatusMapper.toStatus(code) == HttpStatus.INTERNAL_SERVER_ERROR)
                .toList())
                .containsExactly(CalcErrorCode.INTERNAL_ERROR);
    }
}
