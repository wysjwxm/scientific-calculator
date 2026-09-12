package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.Associativity;
import com.wysjwxm.calculator.domain.model.expression.BinaryExpr;
import com.wysjwxm.calculator.domain.model.expression.CallExpr;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.ExpressionText;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import com.wysjwxm.calculator.domain.model.expression.Operator;
import com.wysjwxm.calculator.domain.model.expression.OperatorTable;
import com.wysjwxm.calculator.domain.model.expression.PostfixExpr;
import com.wysjwxm.calculator.domain.model.expression.UnaryExpr;
import com.wysjwxm.calculator.domain.model.expression.VariableExpr;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 优先级爬升（precedence climbing）式递归下降解析器。
 *
 * <p><b>无状态。</b>解析所需的游标（tokens + index）封在每次调用创建的
 * {@link ParseRun} 里，因此本类可以被多线程安全共享，注册为 Spring 单例无风险。
 * 若把游标做成实例字段，并发请求会互相踩踏。
 *
 * <p>优先级与结合性全部来自 {@link OperatorTable}，解析器自身不含任何硬编码的
 * 优先级数值 —— 这样 /functions 能力清单（同样由 OperatorTable 生成）不可能与
 * 实际解析行为不一致。
 */
public final class ExpressionParser {

    public Expression parse(ExpressionText text) {
        return new ParseRun(text.value()).parseRoot();
    }

    /** 单次解析的运行状态。每次 parse 创建一个，不跨调用共享。 */
    private static final class ParseRun {

        /** 最松的结合层级，作为入口。 */
        private static final int MIN_PRECEDENCE = 0;

        private final List<Token> tokens;
        private int index;

        ParseRun(String input) {
            this.tokens = new Lexer(input).tokenize();
        }

        Expression parseRoot() {
            Expression result = parseExpression(MIN_PRECEDENCE);
            Token trailing = peek();
            if (trailing.type() != TokenType.EOF) {
                throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                        "表达式在 '" + trailing.lexeme() + "' 处出现多余内容", trailing.position());
            }
            return result;
        }

        /**
         * 核心循环：先解析一个一元前缀或基本项，然后不断吸收优先级不低于
         * minPrecedence 的中缀与后缀算子。
         *
         * @param minPrecedence 当前上下文允许吸收的最低优先级；左结合算子递归时
         *                      传 precedence+1，右结合传 precedence，由此实现结合性
         */
        private Expression parseExpression(int minPrecedence) {
            Expression left = parseUnary();
            while (true) {
                Token token = peek();

                Optional<Operator> infix = OperatorTable.infix(token.type());
                if (infix.isPresent() && infix.get().precedence() >= minPrecedence) {
                    Operator op = infix.get();
                    advance();
                    int nextMin = op.associativity() == Associativity.LEFT
                            ? op.precedence() + 1
                            : op.precedence();
                    left = new BinaryExpr(op, left, parseExpression(nextMin));
                    continue;
                }

                Optional<Operator> postfix = OperatorTable.postfix(token.type());
                if (postfix.isPresent() && postfix.get().precedence() >= minPrecedence) {
                    Operator op = postfix.get();
                    advance();
                    left = new PostfixExpr(op, left);
                    continue;
                }

                return left;
            }
        }

        private Expression parseUnary() {
            Token token = peek();
            Optional<Operator> prefix = OperatorTable.prefix(token.type());
            if (prefix.isPresent()) {
                Operator op = prefix.get();
                advance();
                // 用算子自身优先级作为下界：一元负号(3)因此不会吞掉 ^(4)，得到 -2^2 == -4
                return new UnaryExpr(op, parseExpression(op.precedence()));
            }
            return parsePrimary();
        }

        private Expression parsePrimary() {
            Token token = peek();
            // 基于枚举常量的 switch 表达式（Java 14 起为标准特性）；
            // 这里没有按 AST 具体类型分派，故不受预览特性的限制。
            return switch (token.type()) {
                case NUMBER -> {
                    advance();
                    yield new LiteralExpr(toNumber(token));
                }
                case IDENT -> {
                    advance();
                    if (peek().type() == TokenType.LPAREN) {
                        yield parseCall(token);
                    }
                    yield new VariableExpr(token.lexeme(), token.position());
                }
                case LPAREN -> {
                    advance();
                    Expression inner = parseExpression(MIN_PRECEDENCE);
                    expect(TokenType.RPAREN, "缺少右括号");
                    yield inner;
                }
                default -> throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                        "表达式不完整或出现意外符号 '" + token.lexeme() + "'", token.position());
            };
        }

        /**
         * 把 NUMBER token 的字面量转成精确数值。
         *
         * <p>刻意用 {@code new BigDecimal(lexeme)} 而不是先转 double —— 字面量的十进制
         * 精确性必须原样保留，否则数值层的 scale 防线（DecimalNumber）就再也触达不到
         * 真实的 HTTP 输入。
         *
         * <p>词法分析器按「任意长度的指数数字串」收词，因此 {@code 1E+2147483648}
         * （Exponent overflow）、{@code 1E+99999999999999}（Too many nonzero exponent
         * digits）、{@code 1E-2147483648}（Scale out of range）都是**合法 token**，
         * 而 BigDecimal 构造器对它们抛的是未检查的 NumberFormatException；不在此拦截
         * 就会一路逃成接口层的 HTTP 500。转成带位置的 PARSE_ERROR，位置指向出问题的字面量。
         */
        private CalcNumber toNumber(Token token) {
            BigDecimal value;
            try {
                value = new BigDecimal(token.lexeme());
            } catch (NumberFormatException e) {
                throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                        "数字字面量 '" + token.lexeme() + "' 超出可表示范围", token.position());
            }
            return new DecimalNumber(value);
        }

        private Expression parseCall(Token nameToken) {
            expect(TokenType.LPAREN, "函数调用缺少左括号");
            List<Expression> arguments = new ArrayList<>();
            if (peek().type() != TokenType.RPAREN) {
                arguments.add(parseExpression(MIN_PRECEDENCE));
                while (peek().type() == TokenType.COMMA) {
                    advance();
                    arguments.add(parseExpression(MIN_PRECEDENCE));
                }
            }
            expect(TokenType.RPAREN, "函数调用 " + nameToken.lexeme() + " 缺少右括号");
            return new CallExpr(nameToken.lexeme(), arguments, nameToken.position());
        }

        private void expect(TokenType expected, String message) {
            Token token = peek();
            if (token.type() != expected) {
                throw CalcException.at(CalcErrorCode.PARSE_ERROR, message, token.position());
            }
            advance();
        }

        private Token peek() {
            return tokens.get(index);
        }

        private void advance() {
            index++;
        }
    }
}
