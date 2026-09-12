package com.wysjwxm.calculator.domain.model.expression.parse;

/**
 * @param position 起始字符下标（0 基），供错误定位使用
 */
public record Token(TokenType type, String lexeme, int position) {
}
