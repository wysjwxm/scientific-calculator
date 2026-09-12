package com.wysjwxm.calculator.interfaces.dto;

import com.wysjwxm.calculator.domain.AngleUnit;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 求值请求体。
 *
 * @param expression 表达式原文，必填
 * @param angleUnit  角度单位，null 表示未指定、取服务端缺省值
 * @param variables  请求级临时变量，仅本次求值生效、不落库
 */
public record CalculateRequest(String expression, AngleUnit angleUnit,
                               Map<String, BigDecimal> variables) {
}
