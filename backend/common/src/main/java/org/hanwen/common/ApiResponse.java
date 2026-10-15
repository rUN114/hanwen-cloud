package org.hanwen.common;

public record ApiResponse<T>(String code, String message, T data, String traceId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "操作成功", data, Trace.id());
    }
}
