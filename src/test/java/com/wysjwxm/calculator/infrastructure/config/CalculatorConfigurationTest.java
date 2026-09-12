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
        // MVP 只装配求值路径的 6 个 Bean。这条断言钉住的是「装配清单」本身：少一个 Bean，
        // 本期没有第二个消费者会报错（上下文照样启动），缺陷要潜伏到 Task 14 的控制器
        // 里才以「找不到 bean」的形式爆出来。
        for (Class<?> type : List.of(Numbers.class, FunctionRegistry.class, ExpressionParser.class,
                ExpressionEvaluator.class, CalculationPolicy.class, CalculationUseCase.class)) {
            assertThat(context.getBeanNamesForType(type))
                    .as("Bean %s 应恰好注册一个", type.getSimpleName())
                    .hasSize(1);
        }
    }

    @Test
    void policyTakesItsValuesFromConfigurationProperties() {
        // 装配的翻译路径：properties → policy。两者取值必须一致，否则 yaml 改了不生效。
        CalculatorProperties properties = context.getBean(CalculatorProperties.class);
        CalculationPolicy policy = context.getBean(CalculationPolicy.class);
        assertThat(policy.defaultAngleUnit()).isEqualTo(properties.defaultAngleUnit());
        assertThat(policy.maxExpressionLength()).isEqualTo(properties.maxExpressionLength());
    }
}
