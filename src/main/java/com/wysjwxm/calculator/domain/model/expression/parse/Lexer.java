package com.wysjwxm.calculator.domain.model.expression.parse;

import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式词法分析器。每个 Token 携带绝对字符位置，供错误定位。
 */
public final class Lexer {

    private final String input;
    private int pos;

    public Lexer(String input) {
        this.input = input == null ? "" : input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            int start = pos;
            if (Character.isDigit(c) || c == '.') {
                tokens.add(readNumber(start));
            } else if (isIdentStart(c)) {
                tokens.add(readIdent(start));
            } else {
                tokens.add(readSymbol(start, c));
            }
        }
        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    private Token readNumber(int start) {
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
        }
        // 指数部分：e/E 后必须紧跟可选符号 + 至少一位数字，否则报错
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            int exponentStart = pos;
            pos++;
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                pos++;
            }
            if (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                    pos++;
                }
            } else {
                throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                        "科学计数法指数部分不完整", exponentStart);
            }
        }
        String lexeme = input.substring(start, pos);
        if (lexeme.equals(".")) {
            throw CalcException.at(CalcErrorCode.PARSE_ERROR, "孤立的小数点不是合法数字", start);
        }
        return new Token(TokenType.NUMBER, lexeme, start);
    }

    private Token readIdent(int start) {
        while (pos < input.length() && isIdentPart(input.charAt(pos))) {
            pos++;
        }
        return new Token(TokenType.IDENT, input.substring(start, pos), start);
    }

    private Token readSymbol(int start, char c) {
        pos++;
        TokenType type = switch (c) {
            case '+' -> TokenType.PLUS;
            case '-' -> TokenType.MINUS;
            case '*' -> TokenType.STAR;
            case '/' -> TokenType.SLASH;
            case '%' -> TokenType.PERCENT;
            case '^' -> TokenType.CARET;
            case '!' -> TokenType.BANG;
            case '(' -> TokenType.LPAREN;
            case ')' -> TokenType.RPAREN;
            case ',' -> TokenType.COMMA;
            default -> throw CalcException.at(CalcErrorCode.PARSE_ERROR,
                    "无法识别的字符 '" + c + "'", start);
        };
        return new Token(type, String.valueOf(c), start);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
