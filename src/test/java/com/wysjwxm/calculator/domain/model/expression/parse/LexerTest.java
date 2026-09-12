package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LexerTest {

    private List<Token> lex(String input) {
        return new Lexer(input).tokenize();
    }

    private List<TokenType> types(String input) {
        return lex(input).stream().map(Token::type).toList();
    }

    @Test
    void tokenizesSimpleExpression() {
        assertThat(types("1+2"))
                .containsExactly(TokenType.NUMBER, TokenType.PLUS, TokenType.NUMBER, TokenType.EOF);
    }

    @Test
    void recordsAbsolutePositions() {
        assertThat(lex("1 + 22")).extracting(Token::position).containsExactly(0, 2, 4, 6);
    }

    @Test
    void tokenizesDecimalWithoutLeadingDigit() {
        assertThat(lex(".5").get(0)).isEqualTo(new Token(TokenType.NUMBER, ".5", 0));
    }

    @Test
    void tokenizesScientificNotation() {
        assertThat(lex("1.5e-3").get(0).lexeme()).isEqualTo("1.5e-3");
        assertThat(lex("1.5E+10").get(0).lexeme()).isEqualTo("1.5E+10");
    }

    @Test
    void doesNotSplitMinusOutOfScientificNotation() {
        assertThat(types("1e-3")).containsExactly(TokenType.NUMBER, TokenType.EOF);
    }

    @Test
    void tokenizesAllOperators() {
        assertThat(types("+-*/%^!"))
                .containsExactly(TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH,
                        TokenType.PERCENT, TokenType.CARET, TokenType.BANG, TokenType.EOF);
    }

    @Test
    void tokenizesIdentifiersWithUnderscoreAndDigits() {
        assertThat(lex("x_1").get(0)).isEqualTo(new Token(TokenType.IDENT, "x_1", 0));
    }

    @Test
    void tokenizesParensAndComma() {
        assertThat(types("(1,2)"))
                .containsExactly(TokenType.LPAREN, TokenType.NUMBER, TokenType.COMMA,
                        TokenType.NUMBER, TokenType.RPAREN, TokenType.EOF);
    }

    @Test
    void skipsWhitespace() {
        assertThat(types("  sin ( 30 )  "))
                .containsExactly(TokenType.IDENT, TokenType.LPAREN, TokenType.NUMBER,
                        TokenType.RPAREN, TokenType.EOF);
    }

    @Test
    void illegalCharacterThrowsWithPosition() {
        assertThatThrownBy(() -> lex("1 + @"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> {
                    CalcException ce = (CalcException) e;
                    assertThat(ce.code()).isEqualTo(CalcErrorCode.PARSE_ERROR);
                    assertThat(ce.position()).isEqualTo(4);
                });
    }

    @Test
    void unterminatedScientificNotationThrows() {
        // 位置指向指数部分的起点（'e' 的下标 1），不是数字的起点 ——
        // 报错的是「指数部分不完整」，读者需要看到的是 'e' 到字符串末尾这一段。
        assertThatThrownBy(() -> lex("1e+"))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(1));
    }

    @Test
    void loneDotIsRejected() {
        assertThatThrownBy(() -> lex("."))
                .isInstanceOf(CalcException.class)
                .satisfies(e -> assertThat(((CalcException) e).position()).isEqualTo(0));
    }

    @Test
    void emptyInputYieldsOnlyEof() {
        assertThat(types("")).containsExactly(TokenType.EOF);
    }
}
