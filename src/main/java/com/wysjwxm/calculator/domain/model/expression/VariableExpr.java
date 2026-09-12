package com.wysjwxm.calculator.domain.model.expression;

/**
 * @param position 标识符在原文中的起始下标，供「未定义变量」报错定位
 */
public record VariableExpr(String name, int position) implements Expression {
}
