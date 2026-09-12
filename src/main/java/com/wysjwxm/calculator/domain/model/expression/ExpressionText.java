package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

/**
 * 表达式原文的值对象：调用方提交的原始文本，仅去除首尾空白，不做规范化改写。
 *
 * <p>只承载**语言层面的不变量**。长度上限是资源保护策略（且来自可配置项），
 * 不属于这里的职责 —— 把它塞进构造器会让语言值对象依赖运行时配置。长度由
 * CalculationUseCase 依据 CalculationPolicy 强制。
 */
public record ExpressionText(String value) {

    public ExpressionText {
        if (value == null) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "expression 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw CalcException.of(CalcErrorCode.INVALID_REQUEST, "expression 不能为空白");
        }
        value = trimmed;
    }
}
