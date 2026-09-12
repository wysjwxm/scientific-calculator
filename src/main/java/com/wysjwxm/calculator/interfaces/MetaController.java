package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.interfaces.dto.HealthResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

/**
 * 元信息端点：健康检查。路径是不带前缀的 /health（spec §3.1、§7.5）。
 */
@RestController
public class MetaController {

    private final Instant startedAt = Instant.now();

    @GetMapping("/health")
    public HealthResponse health() {
        return new HealthResponse("UP", Duration.between(startedAt, Instant.now()).toMillis());
    }
}
