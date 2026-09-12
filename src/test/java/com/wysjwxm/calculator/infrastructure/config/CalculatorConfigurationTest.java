package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装配与配置绑定的守门人。
 */
@SpringBootTest
class CalculatorConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private Environment environment;

    @Test
    void bindsAllFourKeysFromTheRealApplicationYaml() {
        // 断言**键存在**，而不只是断言值：application.yaml 里四个键的值恰好都等于
        // CalculatorProperties 的 @DefaultValue，所以键名拼错时缺省值会顶上来、
        // 绑出来的对象一模一样 —— 只断言值抓不到这种 typo。键名逐字写死，改键名必红。
        assertThat(environment.containsProperty("calculator.default-angle-unit")).isTrue();
        assertThat(environment.containsProperty("calculator.history-capacity")).isTrue();
        assertThat(environment.containsProperty("calculator.max-expression-length")).isTrue();
        assertThat(environment.containsProperty("calculator.division-precision")).isTrue();

        // 值也断言：钉住「record 分量 ↔ yaml 键」的对应关系没有错位。
        CalculatorProperties properties = context.getBean(CalculatorProperties.class);
        assertThat(properties.defaultAngleUnit()).isEqualTo(AngleUnit.DEGREE);
        assertThat(properties.historyCapacity()).isEqualTo(1000);
        assertThat(properties.maxExpressionLength()).isEqualTo(1000);
        assertThat(properties.divisionPrecision()).isEqualTo(34);
    }

    @Test
    void registersExactlyTheSixEvaluationPathBeans() {
        // MVP 只装配求值路径的 6 个 Bean。这条断言钉住的是「装配清单」本身。
        // 其中 5 个都有下游消费者（ExpressionEvaluator 依赖 numbers 与 functionRegistry；
        // CalculationUseCase 依赖 expressionParser、expressionEvaluator 与 calculationPolicy），
        // 漏装它们会让上下文**启动失败**。只有 CalculationUseCase 在本期没有任何消费者 ——
        // 要到 Task 14 的控制器才有人引用它 —— 漏装它时上下文照常起得来，
        // 这里就成了唯一的探测器。
        for (Class<?> type : List.of(Numbers.class, FunctionRegistry.class, ExpressionParser.class,
                ExpressionEvaluator.class, CalculationPolicy.class, CalculationUseCase.class)) {
            assertThat(context.getBeanNamesForType(type))
                    .as("Bean %s 应恰好注册一个", type.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    void policyTakesItsValuesFromConfigurationProperties() {
        // properties → policy 的装配翻译路径：断言两者取值一致，钉住「分量对应关系」没接错
        // （如把 defaultAngleUnit 与 maxExpressionLength 接反）。
        // 注意这条对照断言**抓不到「装配处写死常量」**：yaml 的值恰好等于 @DefaultValue，
        // 写死 1000 / DEGREE 与读 properties 在这里结果完全相同。
        // 钉住翻译路径的是 CalculatorConfigurationOverrideTest（换一组属性值再断言）。
        CalculatorProperties properties = context.getBean(CalculatorProperties.class);
        CalculationPolicy policy = context.getBean(CalculationPolicy.class);
        assertThat(policy.defaultAngleUnit()).isEqualTo(properties.defaultAngleUnit());
        assertThat(policy.maxExpressionLength()).isEqualTo(properties.maxExpressionLength());
    }
}
