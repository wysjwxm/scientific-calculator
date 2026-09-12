package com.wysjwxm.calculator.application;

import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.MathematicalConstant;
import com.wysjwxm.calculator.domain.model.expression.OperatorTable;
import com.wysjwxm.calculator.domain.model.function.BinaryFunction;
import com.wysjwxm.calculator.domain.model.function.UnaryFunction;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.domain.model.number.Numbers;

import java.util.Arrays;
import java.util.List;

/**
 * 能力清单用例：把「本服务能接受什么」拼成一份可直接序列化的读模型。
 *
 * <p>除 {@link CalculationPolicy} 外不再注入任何东西 —— 常量、函数、算子表都是编译期
 * 固定的领域枚举与静态表，注入它们只是多一层没有信息的转发。
 *
 * <p><b>清单一律从领域枚举与 {@link OperatorTable} 生成，本类不另写任何一张表</b>
 * （spec §7.2 与 D8：清单与实现的漂移必须从结构上不可能，而不是靠人肉同步）。
 * 唯一由外部传入的是 {@code endpoints} —— 路由是接口层才知道的事实，应用层不认识
 * HTTP 路径。
 *
 * <p>顺序即契约：常量、一元函数、二元函数保持各自的枚举**声明顺序**，算子保持
 * {@link OperatorTable#all()} 的顺序，全程不排序、不去重。
 */
public class CapabilityQuery {

    private final CalculationPolicy policy;

    public CapabilityQuery(CalculationPolicy policy) {
        this.policy = policy;
    }

    /** 拼出完整清单。{@code endpoints} 由接口层提供，原样放入、不做加工。 */
    public CapabilityManifest describe(List<ApiEndpoint> endpoints) {
        return new CapabilityManifest(
                endpoints,
                constants(),
                unaryFunctions(),
                binaryFunctions(),
                OperatorTable.all(),
                List.of(AngleUnit.values()),
                policy.defaultAngleUnit(),
                new Limits(policy.maxExpressionLength(), policy.divisionPrecision(),
                        Numbers.MAX_EXACT_DIGITS, DecimalNumber.MAX_SCALE_MAGNITUDE));
    }

    /** 内置常量，按枚举声明顺序。 */
    private static List<ConstantDescription> constants() {
        return Arrays.stream(MathematicalConstant.values())
                .map(c -> new ConstantDescription(c.symbol(), c.value().toDouble(), c.description()))
                .toList();
    }

    /** 一元函数，按枚举声明顺序；定义域文案取自 {@code Domain} 自身。 */
    private static List<FunctionDescription> unaryFunctions() {
        return Arrays.stream(UnaryFunction.values())
                .map(f -> new FunctionDescription(f.functionName(), f.arity(), f.angleSensitive(),
                        f.domain().description(), f.description()))
                .toList();
    }

    /** 二元函数，按枚举声明顺序；定义域文案取自枚举里的 {@code domainDescription}。 */
    private static List<FunctionDescription> binaryFunctions() {
        return Arrays.stream(BinaryFunction.values())
                .map(f -> new FunctionDescription(f.functionName(), f.arity(), f.angleSensitive(),
                        f.domainDescription(), f.description()))
                .toList();
    }
}
