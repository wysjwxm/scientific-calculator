package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.domain.AngleUnit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class CalculatorPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EnableProps.class);

    @EnableConfigurationProperties(CalculatorProperties.class)
    static class EnableProps { }

    @Test
    void bindsValuesFromProperties() {
        runner.withPropertyValues(
                "calculator.default-angle-unit=RADIAN",
                "calculator.history-capacity=50",
                "calculator.max-expression-length=200",
                "calculator.division-precision=16")
             .run(ctx -> {
                 CalculatorProperties props = ctx.getBean(CalculatorProperties.class);
                 assertThat(props.defaultAngleUnit()).isEqualTo(AngleUnit.RADIAN);
                 assertThat(props.historyCapacity()).isEqualTo(50);
                 assertThat(props.maxExpressionLength()).isEqualTo(200);
                 assertThat(props.divisionPrecision()).isEqualTo(16);
             });
    }

    @Test
    void rejectsNonPositiveExpressionLength() {
        runner.withPropertyValues("calculator.max-expression-length=0")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsNonPositiveDivisionPrecision() {
        runner.withPropertyValues("calculator.division-precision=-1")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void rejectsUnknownAngleUnit() {
        runner.withPropertyValues("calculator.default-angle-unit=GRADIANS")
              .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void historyCapacityZeroMeansUnbounded() {
        runner.withPropertyValues("calculator.history-capacity=0")
              .run(ctx -> {
                  CalculatorProperties props = ctx.getBean(CalculatorProperties.class);
                  assertThat(props.historyUnbounded()).isTrue();
              });
    }
}
