package com.wysjwxm.calculator.domain.model.expression;

public record UnaryExpr(Operator operator, Expression operand) implements Expression {
}
