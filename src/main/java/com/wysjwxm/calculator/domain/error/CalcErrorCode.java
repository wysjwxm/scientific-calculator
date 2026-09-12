package com.wysjwxm.calculator.domain.error;

/**
 * 全项目统一的错误码词汇表。
 *
 * <p>刻意不携带 HTTP 状态码 —— 传输协议是接口层的关切，领域异常不该知道 HTTP
 * 的存在。映射由 interfaces 层的 ErrorStatusMapper 集中承担（见 spec §7.6）。
 */
public enum CalcErrorCode {
    PARSE_ERROR,
    INVALID_REQUEST,
    UNKNOWN_FUNCTION,
    VARIABLE_NOT_FOUND,
    HISTORY_NOT_FOUND,
    NO_HANDLER,
    METHOD_NOT_ALLOWED,
    UNKNOWN_VARIABLE,
    DIVISION_BY_ZERO,
    DOMAIN_ERROR,
    NON_FINITE_RESULT,
    INTERNAL_ERROR
}
