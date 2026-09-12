package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.number.Numbers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置**翻译路径**的反向守门人：把属性值改掉，看 Bean 是否跟着变。
 *
 * <p>为什么必须换一组值：application.yaml 里的四个值恰好都等于 {@code @DefaultValue}，
 * 于是「Bean 从 properties 取值」与「Bean 里写死常量」在默认上下文里**表现完全一致** ——
 * {@link CalculatorConfigurationTest} 的对照断言对这两种实现都通过，抓不到写死。
 * 只有让属性值不同于任何可能被写死的常量，装配处的翻译路径才真正被钉住。
 */
class CalculatorConfigurationOverrideTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(CalculatorConfiguration.class)
            .withPropertyValues(
                    "calculator.division-precision=7",
                    "calculator.max-expression-length=42",
                    "calculator.default-angle-unit=RADIAN");

    @Test
    void divisionPrecisionOverrideReachesNumbers() {
        runner.run(ctx -> {
            Numbers numbers = ctx.getBean(Numbers.class);
            // 1/3 除不尽，只能按注入的精度截断：精度 7 → 0.3333333；
            // 若装配处写死 34，这里会得到 34 位小数，对照失败。
            assertThat(numbers.divide(numbers.of(1), numbers.of(3)).toDecimal())
                    .isEqualByComparingTo("0.3333333");
        });
    }

    @Test
    void policyOverridesReachCalculationPolicy() {
        runner.run(ctx -> {
            CalculationPolicy policy = ctx.getBean(CalculationPolicy.class);
            assertThat(policy.maxExpressionLength()).isEqualTo(42);
            assertThat(policy.defaultAngleUnit()).isEqualTo(AngleUnit.RADIAN);
        });
    }
}
