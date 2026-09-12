package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 错误码 → HTTP 状态的唯一映射点。
 *
 * <p>领域层的 {@link CalcErrorCode} 刻意不携带 HTTP 状态，以保持对传输协议无感知；
 * 映射集中在这里，由 ErrorStatusMapperTest 保证不漏。
 */
public final class ErrorStatusMapper {

    private ErrorStatusMapper() {
    }

    public static HttpStatus toStatus(CalcErrorCode code) {
        return switch (code) {
            case PARSE_ERROR, INVALID_REQUEST, UNKNOWN_FUNCTION -> HttpStatus.BAD_REQUEST;
            case VARIABLE_NOT_FOUND, HISTORY_NOT_FOUND, NO_HANDLER -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNKNOWN_VARIABLE, DIVISION_BY_ZERO, DOMAIN_ERROR, NON_FINITE_RESULT ->
                    HttpStatus.UNPROCESSABLE_ENTITY;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
