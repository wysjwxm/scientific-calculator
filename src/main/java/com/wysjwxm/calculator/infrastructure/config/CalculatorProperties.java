package com.wysjwxm.calculator.infrastructure.config;

import com.wysjwxm.calculator.domain.AngleUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 配置绑定类。属基础设施关切（它认识 Spring），因此放在 infrastructure 而非 domain。
 *
 * <p>它不直接注入到 application 层 —— 由同包的 CalculatorConfiguration 翻译成
 * 下游能用的 bean（Numbers / CalculationPolicy 等），避免 application 反向依赖
 * infrastructure。
 *
 * <p>各分量带 {@code @DefaultValue}，与 application.yaml 中的取值保持一致，使配置项
 * 缺省时仍能正常绑定；显式配置的值（含非法值）优先于默认值。
 *
 * <p>取值范围在紧凑构造器中校验：配置非法时 bean 创建即失败，应用启动期快速失败。
 * 不使用 jakarta.validation 注解 —— spring-boot-starter-web 并不传递引入校验实现，
 * 注解会静默失效，反而不如直接抛异常可靠。
 */
@ConfigurationProperties(prefix = "calculator")
public record CalculatorProperties(
        @DefaultValue("DEGREE") AngleUnit defaultAngleUnit,
        @DefaultValue("1000") int maxExpressionLength,
        @DefaultValue("34") int divisionPrecision,
        @DefaultValue("1000") int historyCapacity
) {
    public CalculatorProperties {
        if (defaultAngleUnit == null) {
            throw new IllegalArgumentException("calculator.default-angle-unit 不能为空");
        }
        if (maxExpressionLength < 1) {
            throw new IllegalArgumentException(
                    "calculator.max-expression-length 必须为正数，当前值：" + maxExpressionLength);
        }
        if (divisionPrecision < 1) {
            throw new IllegalArgumentException(
                    "calculator.division-precision 必须为正数，当前值：" + divisionPrecision);
        }
    }

    /** historyCapacity 允许为 0 或负数，语义为「不限制容量」，因此不校验下界。 */
    public boolean historyUnbounded() {
        return historyCapacity <= 0;
    }
}
