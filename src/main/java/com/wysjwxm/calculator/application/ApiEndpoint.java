package com.wysjwxm.calculator.application;

/** 一个 HTTP 端点。只有接口层知道路由，故由接口层传入。 */
public record ApiEndpoint(String method, String path, String description) {
}
