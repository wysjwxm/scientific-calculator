package com.wysjwxm.calculator.interfaces;

import com.wysjwxm.calculator.application.ApiEndpoint;
import com.wysjwxm.calculator.application.CapabilityManifest;
import com.wysjwxm.calculator.application.CapabilityQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 能力清单端点（spec §7.2）：一次返回本服务可用的接口、函数说明与能力边界。
 *
 * <p>与 {@link CalculatorController} 的基路径一致，只有 {@code /functions} 一个方法 ——
 * 刻意不做 {@code /functions/{name}} 单条查询：清单的全部价值在于一次拿全，
 * 单条查询既省不了流量，又要把同一份枚举再检索一遍。
 *
 * <p>{@link #ENDPOINTS} 是本类唯一手写的数据 —— HTTP 路由只有接口层知道，故由接口层
 * 提供给应用层。它是手写常量，因此存在漂移风险：真正的防漂移网是
 * {@code CapabilityEndToEndTest.manifestEndpointsMatchTheActuallyRegisteredRoutes}
 * （从 {@code RequestMappingHandlerMapping} 取真实注册路由对账）。改路由时若忘了改这里，
 * 那条测试会变红。
 *
 * <p>返回的读模型不需要任何 {@code @JsonSerialize}：里面没有 {@code CalcNumber} 之类的
 * 领域抽象，{@code Operator} 是普通 record、{@code AngleUnit} 是枚举，Jackson 直接写出来。
 */
@RestController
@RequestMapping("/api/v1/calculator")
class CapabilityController {

    /** 本服务对外提供的全部接口，按路由的稳定顺序排列。 */
    private static final List<ApiEndpoint> ENDPOINTS = List.of(
            new ApiEndpoint("GET", "/health", "健康检查：返回服务状态与已运行时长"),
            new ApiEndpoint("POST", "/api/v1/calculator/calculate", "求值一个表达式"),
            new ApiEndpoint("GET", "/api/v1/calculator/functions", "本接口：服务能力清单"));

    private final CapabilityQuery capabilityQuery;

    CapabilityController(CapabilityQuery capabilityQuery) {
        this.capabilityQuery = capabilityQuery;
    }

    @GetMapping("/functions")
    public CapabilityManifest functions() {
        return capabilityQuery.describe(ENDPOINTS);
    }
}
