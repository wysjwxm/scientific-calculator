package com.wysjwxm.calculator.domain.model.expression;

import java.util.List;

/**
 * @param position 函数名在原文中的起始下标，供「未知函数」报错定位
 */
public record CallExpr(String functionName, List<Expression> arguments, int position)
        implements Expression {

    public CallExpr {
        arguments = List.copyOf(arguments);
    }
}
