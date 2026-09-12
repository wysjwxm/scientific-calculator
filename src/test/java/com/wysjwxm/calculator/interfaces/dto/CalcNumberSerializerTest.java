package com.wysjwxm.calculator.interfaces.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.domain.model.number.CalcNumber;
import com.wysjwxm.calculator.domain.model.number.DecimalNumber;
import com.wysjwxm.calculator.domain.model.number.FloatingNumber;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CalcNumberSerializer 的线上形态。补的是此前完全空缺的一层：领域层断言的是
 * CalcNumber，HTTP 响应里是 JSON 数字，中间这段（Jackson + 本序列化器）此前没有测试。
 */
class CalcNumberSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 复现 CalculateResponse.result 上的注解用法，把序列化器本身隔离出来 */
    private record Holder(@JsonSerialize(using = CalcNumberSerializer.class) CalcNumber value) {
    }

    private String json(CalcNumber value) throws Exception {
        return objectMapper.writeValueAsString(new Holder(value));
    }

    private static DecimalNumber decimal(String literal) {
        return new DecimalNumber(new BigDecimal(literal));
    }

    @Test
    void integralExactResultsDoNotUseScientificNotation() throws Exception {
        // BigDecimal("100").stripTrailingZeros().toString() 是 1E+2，
        // 直接 writeNumber(BigDecimal) 会把它原样写出去
        assertThat(json(decimal("100"))).isEqualTo("{\"value\":100}");
        assertThat(json(decimal("20"))).isEqualTo("{\"value\":20}");
        assertThat(json(decimal("1000"))).isEqualTo("{\"value\":1000}");
    }

    @Test
    void exactDecimalsKeepTheirPlainForm() throws Exception {
        assertThat(json(decimal("0.3"))).isEqualTo("{\"value\":0.3}");
        assertThat(json(decimal("1.5"))).isEqualTo("{\"value\":1.5}");
    }

    @Test
    void trailingZerosAreStripped() throws Exception {
        assertThat(json(decimal("12.00"))).isEqualTo("{\"value\":12}");
    }

    @Test
    void zeroIsWrittenAsASingleZero() throws Exception {
        assertThat(json(decimal("0"))).isEqualTo("{\"value\":0}");
    }

    @Test
    void floatingPathIsWrittenAsDouble() throws Exception {
        assertThat(json(new FloatingNumber(4.0))).isEqualTo("{\"value\":4.0}");
    }

    @Test
    void realResponseBodyShowsPlainDecimalForIntegralResult() throws Exception {
        // 用户手工验收问到的就是这一条：2*50 在线上长什么样
        CalculateResponse response =
                new CalculateResponse("2*50", decimal("100"), "DECIMAL", AngleUnit.DEGREE);
        assertThat(objectMapper.writeValueAsString(response))
                .isEqualTo("{\"expression\":\"2*50\",\"result\":100,"
                        + "\"resultType\":\"DECIMAL\",\"angleUnit\":\"DEGREE\"}");
    }
}
