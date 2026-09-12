package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.CalculationPolicy;
import com.wysjwxm.calculator.application.CapabilityQuery;
import com.wysjwxm.calculator.domain.AngleUnit;
import com.wysjwxm.calculator.interfaces.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CapabilityControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CapabilityQuery query =
                new CapabilityQuery(new CalculationPolicy(AngleUnit.DEGREE, 1000, 34));
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CapabilityController(query))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsTheFullCapabilityManifest() throws Exception {
        mockMvc.perform(get("/api/v1/calculator/functions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoints.length()").value(3))
                .andExpect(jsonPath("$.constants.length()").value(2))
                .andExpect(jsonPath("$.unaryFunctions.length()").value(23))
                .andExpect(jsonPath("$.binaryFunctions.length()").value(5))
                .andExpect(jsonPath("$.operators.length()").value(9))
                .andExpect(jsonPath("$.angleUnits.length()").value(2))
                .andExpect(jsonPath("$.defaultAngleUnit").value("DEGREE"))
                .andExpect(jsonPath("$.unaryFunctions[0].name").value("sin"))
                .andExpect(jsonPath("$.unaryFunctions[0].arity").value(1))
                .andExpect(jsonPath("$.unaryFunctions[0].angleSensitive").value(true))
                .andExpect(jsonPath("$.unaryFunctions[0].domain").value("任意实数"))
                .andExpect(jsonPath("$.unaryFunctions[0].description")
                        .value("正弦。入参按 angleUnit 解释为角度或弧度，返回比值"))
                .andExpect(jsonPath("$.binaryFunctions[4].description")
                        .value("对数 log(真数, 底数)"))
                .andExpect(jsonPath("$.binaryFunctions[4].name").value("log"))
                .andExpect(jsonPath("$.binaryFunctions[4].domain").value("真数 > 0，且底数 > 0 且底数 ≠ 1"))
                .andExpect(jsonPath("$.operators[0].symbol").value("+"))
                .andExpect(jsonPath("$.operators[0].precedence").value(1))
                .andExpect(jsonPath("$.limits.divisionPrecision").value(34))
                .andExpect(jsonPath("$.limits.maxExpressionLength").value(1000));
    }
}
