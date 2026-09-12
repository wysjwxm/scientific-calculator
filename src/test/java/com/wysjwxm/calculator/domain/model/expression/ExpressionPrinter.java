package com.wysjwxm.calculator.domain.model.expression;

import java.util.stream.Collectors;

/**
 * 把 AST 打印成完全括号化的中缀文本，**仅用于测试断言与调试**，因此放在 test 源码。
 *
 * <p>每个二元运算都加括号，字符串本身就唯一确定了树的结构 —— 这让优先级与
 * 结合性的测试一眼可读，比逐层 getter 断言更不易看错。
 *
 * <p>按节点类型分派刻意写成 {@code instanceof} 模式链而非 {@code switch} 类型模式：
 * 后者在 Java 17 仍是预览特性，需要 {@code --enable-preview} 才能编译，会让交付物
 * 无法用 {@code java -jar} 直接运行。链尾的兜底分支是编译器无法穷尽检查的代价 ——
 * 新增节点类型时不会有编译错误提示，故这里显式抛错，让遗漏在测试中立刻暴露。
 */
public final class ExpressionPrinter {

    private ExpressionPrinter() {
    }

    public static String toInfix(Expression expr) {
        if (expr instanceof LiteralExpr l) {
            return l.value().toDecimal().stripTrailingZeros().toPlainString();
        }
        if (expr instanceof VariableExpr v) {
            return v.name();
        }
        if (expr instanceof UnaryExpr u) {
            return "(" + u.operator().symbol() + toInfix(u.operand()) + ")";
        }
        if (expr instanceof PostfixExpr p) {
            return "(" + toInfix(p.operand()) + p.operator().symbol() + ")";
        }
        if (expr instanceof BinaryExpr b) {
            return "(" + toInfix(b.left()) + " " + b.operator().symbol()
                    + " " + toInfix(b.right()) + ")";
        }
        if (expr instanceof CallExpr c) {
            return c.functionName() + "(" + c.arguments().stream()
                    .map(ExpressionPrinter::toInfix)
                    .collect(Collectors.joining(", ")) + ")";
        }
        throw new IllegalStateException("未覆盖的表达式节点类型: " + expr.getClass());
    }
}
