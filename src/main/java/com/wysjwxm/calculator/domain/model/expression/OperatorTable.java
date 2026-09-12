package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.model.expression.parse.TokenType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 算子优先级与结合性的唯一事实来源。
 *
 * <p>解析器与 /functions 能力清单都从这份数据读取，因此两者不可能漂移 ——
 * 这是刻意的设计：若把优先级硬编码进解析器的嵌套方法，同样的数值会被写两遍，
 * 而防漂移测试会变得形同虚设（清单与数据一致，但两者同时与解析器行为不符）。
 */
public final class OperatorTable {

    private static final Map<TokenType, Operator> INFIX = new EnumMap<>(TokenType.class);
    private static final Map<TokenType, Operator> PREFIX = new EnumMap<>(TokenType.class);
    private static final Map<TokenType, Operator> POSTFIX = new EnumMap<>(TokenType.class);
    private static final List<Operator> ALL;

    static {
        Operator plus = new Operator("+", Fixity.INFIX, 1, Associativity.LEFT);
        Operator minus = new Operator("-", Fixity.INFIX, 1, Associativity.LEFT);
        Operator star = new Operator("*", Fixity.INFIX, 2, Associativity.LEFT);
        Operator slash = new Operator("/", Fixity.INFIX, 2, Associativity.LEFT);
        Operator percent = new Operator("%", Fixity.INFIX, 2, Associativity.LEFT);
        Operator caret = new Operator("^", Fixity.INFIX, 4, Associativity.RIGHT);
        Operator unaryPlus = new Operator("+", Fixity.PREFIX, 3, null);
        Operator unaryMinus = new Operator("-", Fixity.PREFIX, 3, null);
        Operator bang = new Operator("!", Fixity.POSTFIX, 5, null);

        INFIX.put(TokenType.PLUS, plus);
        INFIX.put(TokenType.MINUS, minus);
        INFIX.put(TokenType.STAR, star);
        INFIX.put(TokenType.SLASH, slash);
        INFIX.put(TokenType.PERCENT, percent);
        INFIX.put(TokenType.CARET, caret);

        PREFIX.put(TokenType.PLUS, unaryPlus);
        PREFIX.put(TokenType.MINUS, unaryMinus);

        POSTFIX.put(TokenType.BANG, bang);

        ALL = List.of(plus, minus, star, slash, percent, caret, unaryPlus, unaryMinus, bang);
    }

    private OperatorTable() {
    }

    public static Optional<Operator> infix(TokenType type) {
        return Optional.ofNullable(INFIX.get(type));
    }

    public static Optional<Operator> prefix(TokenType type) {
        return Optional.ofNullable(PREFIX.get(type));
    }

    public static Optional<Operator> postfix(TokenType type) {
        return Optional.ofNullable(POSTFIX.get(type));
    }

    /** 全部算子条目，供 /functions 能力清单生成。 */
    public static List<Operator> all() {
        return ALL;
    }
}
