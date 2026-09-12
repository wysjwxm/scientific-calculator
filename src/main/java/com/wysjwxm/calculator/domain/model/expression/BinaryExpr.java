package com.wysjwxm.calculator.domain.model.expression;

public record BinaryExpr(Operator operator, Expression left, Expression right) implements Expression {
}
