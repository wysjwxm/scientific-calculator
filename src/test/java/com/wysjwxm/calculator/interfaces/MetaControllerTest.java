package com.wysjwxm.calculator.interfaces;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MetaControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new MetaController())
            .build();

    @Test
    void healthReportsUp() throws Exception {
        // 健康检查路径是不带前缀的 /health（spec §3.1 与 §7.5）
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.uptimeMs").isNumber());
    }
}
