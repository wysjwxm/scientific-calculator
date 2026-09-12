package com.wysjwxm.calculator;

import com.wysjwxm.calculator.application.ApiEndpoint;
import com.wysjwxm.calculator.application.CapabilityManifest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力清单端点的真实 HTTP 栈验证：内嵌 Tomcat + 真实 Spring 上下文 + 真实 Jackson 配置。
 *
 * <p>与 {@link CalculatorEndToEndTest} 同款基座（RANDOM_PORT + TestRestTemplate），
 * 是 {@code mvn test} 的一部分 —— 内嵌容器在测试 JVM 内部、临时端口、测试结束即关。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CapabilityEndToEndTest {

    /** 本项目的控制器包。防漂移对账只关心这个包里的路由，Spring 自身的（如 /error）不算。 */
    private static final String INTERFACES_PACKAGE = "com.wysjwxm.calculator.interfaces";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private CapabilityManifest fetchManifest() {
        ResponseEntity<CapabilityManifest> response =
                rest.getForEntity(url("/api/v1/calculator/functions"), CapabilityManifest.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    @Test
    void manifestIsServedOverRealHttp() {
        CapabilityManifest manifest = fetchManifest();

        assertThat(manifest.unaryFunctions()).hasSize(23);
        assertThat(manifest.defaultAngleUnit().name()).isEqualTo("DEGREE");
    }

    /**
     * 防漂移：清单里的 {@code endpoints} 必须与实际注册的路由一致。
     *
     * <p><b>{@code endpoints} 是控制器里的手写常量，这条测试是它唯一的防漂移网。</b>
     * 手写的接口清单会声称存在并不存在的路由（或漏掉真有的），而调用方据此发出的请求
     * 只会拿到 404 —— 那是比「清单里缺一个字段」更严重的不实。故这里不信任注释、也不
     * 信任常量本身，而是从 {@link RequestMappingHandlerMapping} 里取路由的真实注册结果来对账。
     *
     * <p>归一化口径：只取控制器类属于 {@value #INTERFACES_PACKAGE} 包的 handler
     * （把 Spring 自己注册的 /error 之类排除在外），每条路由记为
     * {@code "METHOD pattern"} 形式 —— 路径用 pattern 原文（如
     * {@code /api/v1/calculator/functions}），方法取条件集合中的每一个 HTTP 方法。
     */
    @Test
    void manifestEndpointsMatchTheActuallyRegisteredRoutes() {
        Set<String> registered = registeredRoutesInInterfacesPackage();

        Set<String> declared = new TreeSet<>();
        for (ApiEndpoint endpoint : fetchManifest().endpoints()) {
            declared.add(endpoint.method() + " " + endpoint.path());
        }

        assertThat(declared).isEqualTo(registered);
    }

    private Set<String> registeredRoutesInInterfacesPackage() {
        Set<String> routes = new TreeSet<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entry.getValue();
            if (!INTERFACES_PACKAGE.equals(handler.getBeanType().getPackageName())) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                for (String pattern : info.getPatternValues()) {
                    routes.add(method.name() + " " + pattern);
                }
            }
        }
        return routes;
    }
}
