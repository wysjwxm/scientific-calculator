package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;

import java.io.IOException;

/**
 * 把 CalcNumber 序列化为 JSON 数字：精确路径写 BigDecimal（Jackson 输出 0.3
 * 而非 0.30000000000000004），浮点路径写 double。
 *
 * <p>这是防腐层的一部分：领域值对象不直接暴露给 Jackson 的默认序列化。
 *
 * <p>精确路径刻意写**普通十进制而非科学计数法**：{@code stripTrailingZeros()} 对末位为 0
 * 的整数会给出负 scale（{@code BigDecimal("100")} 去掉尾零后 scale 为 -2），其
 * {@code toString()} 随之变成 {@code 1E+2} 这种形态。它是合法 JSON 数字、也能被原样读回，
 * 但对读接口的人是意外 —— 所以这里改用 {@code toPlainString()}，{@code 2*50} 的线上形态
 * 就是 {@code 100}。注意 {@code gen.writeNumber(String)} **不加引号**，写出去仍是 JSON
 * 数字而非字符串，契约形态不变。
 *
 * <p>代价如实记在这里：负 scale 的绝对值上限由 {@link DecimalNumber} 限死在 100_000，
 * 所以最坏情况下 {@code 1E+100000} 这类值会从 9 个字符膨胀成约 10 万字符的响应体。
 * 这是个远端边角，本期接受，不为它写特殊分支（用户已明确「过于边界的情况可以直接拒绝，
 * 不需要完美」）。
 */
public class CalcNumberSerializer extends JsonSerializer<CalcNumber> {

    @Override
    public void serialize(CalcNumber value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value instanceof DecimalNumber decimal) {
            gen.writeNumber(decimal.value().stripTrailingZeros().toPlainString());
        } else {
            gen.writeNumber(value.toDouble());
        }
    }
}
