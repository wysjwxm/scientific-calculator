package com.wysjwxm.calculator.interfaces.dto;

/**
 * 健康检查响应。
 *
 * @param status   固定为 UP，能构造出响应即说明上下文已就绪
 * @param uptimeMs 进程启动至今的毫秒数
 */
public record HealthResponse(String status, long uptimeMs) {
}
