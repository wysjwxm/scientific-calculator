package com.wysjwxm.calculator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 HTTP 栈的全链路验证：内嵌 Tomcat + 真实 Spring 上下文 + 真实 Jackson 配置，
 * 覆盖 spec §14 的验收标准。
 *
 * <p>用 RANDOM_PORT 而非固定端口：开发机上 8080 可能已被占用，撞车会让测试变成
 * 环境问题的假阳性。端口由 {@code @LocalServerPort} 注入。
 *
 * <p>它是 {@code mvn test} 的一部分 —— 内嵌容器在测试 JVM 内部、临时端口、测试结束
 * 即关，不是常驻服务。
 *
 * <p>本期是 MVP：断言只覆盖求值路径（没有历史、变量存储与能力清单）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CalculatorEndToEndTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    // ---------- 应用启动与健康检查 ----------

    @Test
    void healthReportsUp() {
        ResponseEntity<String> health = rest.getForEntity(url("/health"), String.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("\"status\":\"UP\"");
    }

    // ---------- spec §14 验收标准 ----------

    @Test
    void specAcceptanceExpressionReturnsOnePointFive() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1 + 2 * sin(30) ^ 2\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calc.getBody()).contains("\"result\":1.5");
    }

    /**
     * 直出 sin(30) 的 0.5，而不是让它被后续运算吸收掉。
     *
     * <p>{@code 1 + 2*sin(30)^2} 对规整**不敏感**：即使 sin(30) 返回未规整的
     * 0.49999999999999994，平方相加后仍会被 15 位规整吸收回 1.5，所以它证明不了
     * 「函数结果被规整」。只有 sin(30) 直出的 0.5 才是 Numbers.floating 收口
     * 在端到端层面的证据。
     */
    @Test
    void functionResultIsNormalized() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"sin(30)\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calc.getBody()).contains("\"result\":0.5");
    }

    @Test
    void decimalPrecisionSurvivesTheWire() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"0.1+0.2\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(calc.getBody()).contains("\"result\":0.3");
    }

    // ---------- 错误路径（真实状态码，不是内嵌 MockMvc 的状态码） ----------

    @Test
    void divisionByZeroReturns422() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1/0\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(calc.getBody()).contains("DIVISION_BY_ZERO");
    }

    @Test
    void incompleteExpressionReturns400() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1+\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(calc.getBody()).contains("PARSE_ERROR");
    }

    @Test
    void unrecognizedCharacterReturns400() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"1$2\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(calc.getBody()).contains("PARSE_ERROR");
    }

    @Test
    void nonFiniteResultReturns422() {
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"0^-1\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(calc.getBody()).contains("NON_FINITE_RESULT");
    }

    @Test
    void unknownPathReturns404WithNoHandlerCode() {
        // 未注册的路径必须是统一的 404 + NO_HANDLER（spec §7.6），
        // 而不是 Boot 默认错误结构、更不是 500 —— 这条钉住 GlobalExceptionHandler
        // 与真实栈路由的接合点（本类的其它用例都打已注册路径，碰不到这里）。
        ResponseEntity<String> missing = rest.getForEntity(url("/api/v1/nope"), String.class);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("NO_HANDLER");
    }

    @Test
    void overlongExpressionReturns400() {
        // 1001 字符，超出 calculator.max-expression-length（1000）
        String expression = "1" + "+1".repeat(500);
        ResponseEntity<String> calc = postJson("/api/v1/calculator/calculate",
                "{\"expression\":\"" + expression + "\"}");

        assertThat(calc.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(calc.getBody()).contains("INVALID_REQUEST");
    }
}
