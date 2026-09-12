package com.wysjwxm.calculator.domain.error;

/**
 * 领域异常。携带错误码与可选的字符位置（仅语法类错误有位置）。
 */
public class CalcException extends RuntimeException {

    private final CalcErrorCode code;
    private final Integer position;

    private CalcException(CalcErrorCode code, String message, Integer position) {
        super(message);
        this.code = code;
        this.position = position;
    }

    public static CalcException of(CalcErrorCode code, String message) {
        return new CalcException(code, message, null);
    }

    public static CalcException at(CalcErrorCode code, String message, int position) {
        return new CalcException(code, message, position);
    }

    public CalcErrorCode code() {
        return code;
    }

    /** 字符位置（0 基），非位置相关错误返回 null。 */
    public Integer position() {
        return position;
    }
}
