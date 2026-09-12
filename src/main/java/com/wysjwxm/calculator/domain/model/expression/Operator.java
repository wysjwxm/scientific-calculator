package com.wysjwxm.calculator.domain.model.expression;

/**
 * @param precedence    数值越大结合越紧
 * @param associativity 仅 INFIX 有意义；PREFIX / POSTFIX 为 null
 */
public record Operator(String symbol, Fixity fixity, int precedence, Associativity associativity) {
}
