package com.wysjwxm.calculator.domain.model.expression.eval;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.MathematicalConstant;
import com.wysjwxm.calculator.domain.error.CalcErrorCode;
import com.wysjwxm.calculator.domain.error.CalcException;
import com.wysjwxm.calculator.domain.model.expression.BinaryExpr;
import com.wysjwxm.calculator.domain.model.expression.CallExpr;
import com.wysjwxm.calculator.domain.model.expression.Expression;
import com.wysjwxm.calculator.domain.model.expression.LiteralExpr;
import com.wysjwxm.calculator.domain.model.expression.PostfixExpr;
import com.wysjwxm.calculator.domain.model.expression.UnaryExpr;
import com.wysjwxm.calculator.domain.model.expression.VariableExpr;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.function.MathFunction;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.ArrayList;
import java.util.List;

/**
 * 表达式 → CalcNumber 的领域服务。
 *
 * <p><b>无状态</b>：求值只需要参数里的三个输入，没有任何跨调用的可变字段，
 * 因此同一实例可被并发复用，注册为 Spring 单例无风险。
 *
 * <p>按节点类型分派刻意写成 {@code instanceof} 模式链而非 {@code switch} 类型模式：
 * 后者在 Java 17 仍是预览特性，需要 {@code --enable-preview} 才能编译，会让交付物
 * 无法用 {@code java -jar} 直接运行。链尾的兜底分支是编译器无法穷尽检查的代价 ——
 * 新增节点类型时不会有编译错误提示，故这里显式抛错，让遗漏在测试中立刻暴露。
 *
 * <p>算术一律交给 {@link Numbers}，本类不自己写 BigDecimal/double 运算：
 * 类型提升规则集中在数值层是刻意的设计（spec §5.2）。
 */
public final class ExpressionEvaluator {

    private final FunctionRegistry functions;
    private final Numbers numbers;

    public ExpressionEvaluator(FunctionRegistry functions, Numbers numbers) {
        this.functions = functions;
        this.numbers = numbers;
    }

    /** 供用例做 AST 内省（判断是否用到角度敏感函数）。 */
    public FunctionRegistry functionRegistry() {
        return functions;
    }

    public CalcNumber evaluate(Expression expr, AngleUnit angleUnit, EvaluationContext context) {
        if (expr instanceof LiteralExpr literal) {
            return literal.value();
        }
        if (expr instanceof VariableExpr variable) {
            return resolveVariable(variable, context);
        }
        if (expr instanceof UnaryExpr unary) {
            return applyUnary(unary, angleUnit, context);
        }
        if (expr instanceof PostfixExpr postfix) {
            return applyPostfix(postfix, angleUnit, context);
        }
        if (expr instanceof BinaryExpr binary) {
            return applyBinary(binary, angleUnit, context);
        }
        if (expr instanceof CallExpr call) {
            return applyCall(call, angleUnit, context);
        }
        throw new IllegalStateException("未覆盖的表达式节点类型: " + expr.getClass());
    }

    private CalcNumber resolveVariable(VariableExpr variable, EvaluationContext context) {
        // 先查用户变量，未命中则查保留常量（spec §6.4）。因两个集合互斥，
        // 这个顺序不影响结果，但保持与规范文字一致。
        // 按字符串查表、不构造名字值对象：表达式里的标识符是任意词法单元，
        // 像 "sin(sin)" 的内层 sin 只应报「未定义」，不应报「名字非法」。
        return context.lookup(variable.name())
                .or(() -> MathematicalConstant.lookup(variable.name()))
                .orElseThrow(() -> CalcException.of(CalcErrorCode.UNKNOWN_VARIABLE,
                        "未定义的变量或常量: " + variable.name()));
    }

    private CalcNumber applyUnary(UnaryExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber operand = evaluate(expr.operand(), angleUnit, context);
        // 前缀 + 是恒等，- 是取负
        if (expr.operator().symbol().equals("-")) {
            return numbers.negate(operand);
        }
        return operand;
    }

    private CalcNumber applyPostfix(PostfixExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber operand = evaluate(expr.operand(), angleUnit, context);
        if (expr.operator().symbol().equals("!")) {
            return numbers.factorial(operand);
        }
        throw CalcException.of(CalcErrorCode.INTERNAL_ERROR,
                "未知的后缀算子: " + expr.operator().symbol());
    }

    private CalcNumber applyBinary(BinaryExpr expr, AngleUnit angleUnit, EvaluationContext context) {
        CalcNumber left = evaluate(expr.left(), angleUnit, context);
        CalcNumber right = evaluate(expr.right(), angleUnit, context);
        // 分派依据 OperatorTable 记录的中缀符号，避免与 TokenType 二次映射。
        // 这是基于字符串常量的 switch 表达式（Java 7 起即标准特性），不是类型模式。
        String symbol = expr.operator().symbol();
        return switch (symbol) {
            case "+" -> numbers.add(left, right);
            case "-" -> numbers.subtract(left, right);
            case "*" -> numbers.multiply(left, right);
            case "/" -> numbers.divide(left, right);
            case "%" -> numbers.modulo(left, right);
            case "^" -> numbers.power(left, right);
            default -> throw CalcException.of(CalcErrorCode.INTERNAL_ERROR, "未知的中缀算子: " + symbol);
        };
    }

    private CalcNumber applyCall(CallExpr call, AngleUnit angleUnit, EvaluationContext context) {
        MathFunction function = functions.find(call.functionName())
                .orElseThrow(() -> CalcException.at(CalcErrorCode.UNKNOWN_FUNCTION,
                        "未知的函数: " + call.functionName(), call.position()));
        if (call.arguments().size() != function.arity()) {
            throw CalcException.at(CalcErrorCode.INVALID_REQUEST,
                    function.functionName() + " 需要 " + function.arity() + " 个参数，实际收到 "
                            + call.arguments().size() + " 个", call.position());
        }
        List<CalcNumber> args = new ArrayList<>(call.arguments().size());
        for (Expression argument : call.arguments()) {
            args.add(evaluate(argument, angleUnit, context));
        }
        return function.apply(args, angleUnit, numbers);
    }
}
