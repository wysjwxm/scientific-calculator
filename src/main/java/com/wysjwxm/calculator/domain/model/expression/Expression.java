package com.wysjwxm.calculator.domain.model.expression;

/**
 * 表达式语法树。sealed 让求值器可以穷尽地覆盖所有节点类型 ——
 * 日后新增节点类型时，用 instanceof 链分派的地方不会得到编译错误提示，
 * 因此求值器必须自己保留一个「未知节点」兜底分支。
 *
 * <p>刻意不用 switch 类型模式来做分派：那在 Java 17 仍是预览特性，
 * 需要 --enable-preview 才能编译，会破坏「java -jar 直接运行」的交付要求。
 */
public sealed interface Expression
        permits LiteralExpr, VariableExpr, UnaryExpr, PostfixExpr, BinaryExpr, CallExpr {
}
