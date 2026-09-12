package com.wysjwxm.calculator.interfaces.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * @param position 仅语法类错误有值，其余为 null；用 JsonInclude 让无位置的响应里
 *                 不出现该字段，避免调用方误判为 0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String code, String message, Integer position,
                            Instant timestamp, String path) {
}
