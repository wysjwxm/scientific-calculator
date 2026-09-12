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
 */
public class CalcNumberSerializer extends JsonSerializer<CalcNumber> {

    @Override
    public void serialize(CalcNumber value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        if (value instanceof DecimalNumber decimal) {
            gen.writeNumber(decimal.value().stripTrailingZeros());
        } else {
            gen.writeNumber(value.toDouble());
        }
    }
}
