package com.wysjwxm.calculator.domain.model.expression;

public record PostfixExpr(Operator operator, Expression operand) implements Expression {
}
