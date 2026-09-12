package com.wysjwxm.calculator.domain.model.expression;

import com.wysjwxm.calculator.domain.model.expression.parse.TokenType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OperatorTable 是算子优先级的唯一事实来源。这些断言把 spec §6.3 的三条规则
 * 钉死在数据上 —— 解析器读的就是这份数据。
 */
class OperatorTableTest {

    @Test
    void precedenceMatchesSpec() {
        assertThat(OperatorTable.infix(TokenType.PLUS).orElseThrow().precedence()).isEqualTo(1);
        assertThat(OperatorTable.infix(TokenType.MINUS).orElseThrow().precedence()).isEqualTo(1);
        assertThat(OperatorTable.infix(TokenType.STAR).orElseThrow().precedence()).isEqualTo(2);
        assertThat(OperatorTable.infix(TokenType.SLASH).orElseThrow().precedence()).isEqualTo(2);
        assertThat(OperatorTable.infix(TokenType.PERCENT).orElseThrow().precedence()).isEqualTo(2);
        assertThat(OperatorTable.prefix(TokenType.PLUS).orElseThrow().precedence()).isEqualTo(3);
        assertThat(OperatorTable.prefix(TokenType.MINUS).orElseThrow().precedence()).isEqualTo(3);
        assertThat(OperatorTable.infix(TokenType.CARET).orElseThrow().precedence()).isEqualTo(4);
        assertThat(OperatorTable.postfix(TokenType.BANG).orElseThrow().precedence()).isEqualTo(5);
    }

    @Test
    void powerIsRightAssociativeAndOthersLeft() {
        assertThat(OperatorTable.infix(TokenType.CARET).orElseThrow().associativity())
                .isEqualTo(Associativity.RIGHT);
        assertThat(OperatorTable.infix(TokenType.PLUS).orElseThrow().associativity())
                .isEqualTo(Associativity.LEFT);
        assertThat(OperatorTable.infix(TokenType.STAR).orElseThrow().associativity())
                .isEqualTo(Associativity.LEFT);
    }

    @Test
    void unaryPrefixBindsLooserThanPower() {
        // spec §6.3：-2^2 == -4，即一元负号优先级(3) 低于 ^(4)
        assertThat(OperatorTable.prefix(TokenType.MINUS).orElseThrow().precedence())
                .isLessThan(OperatorTable.infix(TokenType.CARET).orElseThrow().precedence());
    }

    @Test
    void postfixBindsTightest() {
        int maxInfix = OperatorTable.all().stream()
                .filter(o -> o.fixity() == Fixity.INFIX)
                .mapToInt(Operator::precedence).max().orElseThrow();
        assertThat(OperatorTable.postfix(TokenType.BANG).orElseThrow().precedence())
                .isGreaterThan(maxInfix);
    }

    @Test
    void nonOperatorTokensResolveToEmpty() {
        assertThat(OperatorTable.infix(TokenType.NUMBER)).isEmpty();
        assertThat(OperatorTable.infix(TokenType.LPAREN)).isEmpty();
        assertThat(OperatorTable.prefix(TokenType.STAR)).isEmpty();
        assertThat(OperatorTable.postfix(TokenType.CARET)).isEmpty();
    }

    @Test
    void allExposesNineOperatorEntries() {
        List<Operator> all = OperatorTable.all();
        assertThat(all).hasSize(9);
        assertThat(all.stream().filter(o -> o.symbol().equals("-"))).hasSize(2);
        assertThat(all.stream().filter(o -> o.symbol().equals("+"))).hasSize(2);
    }

    @Test
    void symbolsUseMathematicalNotationNotNames() {
        // 清单里出现的是 + 而不是 "add" —— 见 spec §12 D3/D8
        assertThat(OperatorTable.all()).extracting(Operator::symbol)
                .containsExactlyInAnyOrder("+", "+", "-", "-", "*", "/", "%", "^", "!");
    }

    @Test
    void prefixAndPostfixOperatorsHaveNoAssociativity() {
        assertThat(OperatorTable.prefix(TokenType.MINUS).orElseThrow().associativity()).isNull();
        assertThat(OperatorTable.postfix(TokenType.BANG).orElseThrow().associativity()).isNull();
    }
}
