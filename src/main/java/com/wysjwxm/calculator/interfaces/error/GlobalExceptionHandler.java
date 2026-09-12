package com.wysjwxm.calculator.interfaces.error;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;

/**
 * 全局异常处理。所有错误响应走同一个结构（spec §7.6）。
 *
 * <p>不依赖 Spring 默认错误页 —— application.yaml 关闭了 whitelabel，并用
 * {@code spring.web.resources.add-mappings: false} 关掉静态资源映射，使未匹配的请求
 * 走 {@link NoHandlerFoundException} 而不是资源处理器，从而能被这里统一处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(CalcException.class)
    public ResponseEntity<ErrorResponse> handleCalc(CalcException ex, HttpServletRequest request) {
        return build(ex.code(), ex.getMessage(), ex.position(), request);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandler(NoHandlerFoundException ex,
                                                         HttpServletRequest request) {
        return build(CalcErrorCode.NO_HANDLER,
                "路径不存在: " + request.getRequestURI(), null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(CalcErrorCode.METHOD_NOT_ALLOWED,
                "该路径不支持 " + ex.getMethod() + " 方法", null, request);
    }

    /** 请求体不是合法 JSON，或字段类型无法绑定。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(CalcErrorCode.INVALID_REQUEST, "请求体不是合法的 JSON 或字段类型不匹配", null, request);
    }

    /** 路径变量或查询参数类型不符，例如 /history/abc。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        return build(CalcErrorCode.INVALID_REQUEST,
                "参数 " + ex.getName() + " 的取值不合法: " + ex.getValue(), null, request);
    }

    /** 兜底。日志留全栈，但不把堆栈外泄给调用方。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未预期的异常, path={}", request.getRequestURI(), ex);
        return build(CalcErrorCode.INTERNAL_ERROR, "服务内部错误", null, request);
    }

    private ResponseEntity<ErrorResponse> build(CalcErrorCode code, String message,
                                                Integer position, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(code.name(), message, position,
                Instant.now(), request.getRequestURI());
        return ResponseEntity.status(ErrorStatusMapper.toStatus(code)).body(body);
    }
}
