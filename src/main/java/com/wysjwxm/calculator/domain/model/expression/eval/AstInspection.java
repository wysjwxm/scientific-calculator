package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.model.expression.BinaryExpr;
import com.wysjwxm.calculator.domain.model.expression.CallExpr;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import com.wysjwxm.calculator.domain.model.expression.PostfixExpr;
import com.wysjwxm.calculator.domain.model.expression.UnaryExpr;
import com.wysjwxm.calculator.domain.model.expression.VariableExpr;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;

/**
 * AST 静态内省。目前只用于判断历史记录里的 angleUnit 是否有意义
 * —— spec §7.3 规定非三角记录该字段为 null。
 *
 * <p>按节点类型分派刻意写成 {@code instanceof} 模式链而非 {@code switch} 类型模式：
 * 后者在 Java 17 仍是预览特性，需要 {@code --enable-preview} 才能编译，会让交付物
 * 无法用 {@code java -jar} 直接运行。链尾的兜底分支是编译器无法穷尽检查的代价 ——
 * 新增节点类型时不会有编译错误提示，故这里显式抛错，让遗漏在测试中立刻暴露。
 */
public final class AstInspection {

    private AstInspection() {
    }

    /** 表达式中是否用到了受角度单位影响的函数。 */
    public static boolean usesAngleSensitiveFunction(Expression expr, FunctionRegistry registry) {
        if (expr instanceof LiteralExpr) {
            return false;
        }
        if (expr instanceof VariableExpr) {
            return false;
        }
        if (expr instanceof UnaryExpr u) {
            return usesAngleSensitiveFunction(u.operand(), registry);
        }
        if (expr instanceof PostfixExpr p) {
            return usesAngleSensitiveFunction(p.operand(), registry);
        }
        if (expr instanceof BinaryExpr b) {
            return usesAngleSensitiveFunction(b.left(), registry)
                    || usesAngleSensitiveFunction(b.right(), registry);
        }
        if (expr instanceof CallExpr c) {
            // 函数本身角度敏感，或任一实参里用到了角度敏感函数
            return registry.find(c.functionName())
                    .map(f -> f.angleSensitive())
                    .orElse(false)
                    || c.arguments().stream()
                            .anyMatch(arg -> usesAngleSensitiveFunction(arg, registry));
        }
        throw new IllegalStateException("未覆盖的表达式节点类型: " + expr.getClass());
    }
}
