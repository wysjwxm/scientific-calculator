package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CalculationUseCase;
import com.wysjwxm.calculator.domain.model.expression.eval.ExpressionEvaluator;
import com.wysjwxm.calculator.domain.model.expression.parse.ExpressionParser;
import com.wysjwxm.calculator.domain.model.function.FunctionRegistry;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 唯一的 Bean 装配点。
 *
 * <p>为什么装配集中在这里、而不是给每个类加 {@code @Service} / {@code @Repository}：
 * 领域层的类**不允许** import Spring，所以它们本来就不能加注解；若只给基础设施层
 * 加注解、领域层走 @Bean，装配点就散成了两处。集中在一处之后，「谁被注册成 Bean」
 * 有唯一答案。
 *
 * <p>这里同时承担翻译职责：把基础设施的 {@link CalculatorProperties} 翻译成
 * 领域与应用能用的对象，因此 application 层不必认识配置类。
 *
 * <p>{@code @EnableConfigurationProperties} 不可省：启动类上只有
 * {@code @SpringBootApplication}，它既不开启 {@code @ConfigurationPropertiesScan}、
 * 也不会扫描 {@code @ConfigurationProperties} 类，所以不写这个注解时
 * {@link CalculatorProperties} 根本不是上下文里的 Bean，application.yaml 里
 * {@code calculator.*} 的键一个都不会被绑定 —— 键名拼错了也没有任何信号。
 *
 * <p>本期是 MVP：只装配求值路径的 6 个 Bean，变量与历史相关的装配
 * （VariableSet / CalculationHistory / 对应用例）属 Phase 2，见 docs/mvp-and-roadmap.md。
 */
@Configuration
@EnableConfigurationProperties(CalculatorProperties.class)
public class CalculatorConfiguration {

    @Bean
    public Numbers numbers(CalculatorProperties properties) {
        return new Numbers(properties.divisionPrecision());
    }

    @Bean
    public FunctionRegistry functionRegistry() {
        return new FunctionRegistry();
    }

    @Bean
    public ExpressionParser expressionParser() {
        // 无状态，可安全作为单例共享
        return new ExpressionParser();
    }

    @Bean
    public ExpressionEvaluator expressionEvaluator(FunctionRegistry functionRegistry,
                                                   Numbers numbers) {
        return new ExpressionEvaluator(functionRegistry, numbers);
    }

    @Bean
    public CalculationPolicy calculationPolicy(CalculatorProperties properties) {
        return new CalculationPolicy(properties.defaultAngleUnit(),
                properties.maxExpressionLength());
    }

    @Bean
    public CalculationUseCase calculationUseCase(ExpressionParser expressionParser,
                                                 ExpressionEvaluator expressionEvaluator,
                                                 CalculationPolicy calculationPolicy) {
        return new CalculationUseCase(expressionParser, expressionEvaluator, calculationPolicy);
    }
}
