package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.model.number.CalcNumber;

public record LiteralExpr(CalcNumber value) implements Expression {
}
