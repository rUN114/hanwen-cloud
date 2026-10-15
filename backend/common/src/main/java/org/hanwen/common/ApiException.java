package org.hanwen.common;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiException bad(String m) {
        return new ApiException(400, "VALIDATION_ERROR", m);
    }

    public static ApiException forbidden() {
        return new ApiException(403, "FORBIDDEN", "无权访问此资源");
    }

    public static ApiException missing() {
        return new ApiException(404, "NOT_FOUND", "资源不存在");
    }

    public static ApiException conflict(String m) {
        return new ApiException(409, "STATE_CONFLICT", m);
    }

    public static ApiException unavailable() {
        return new ApiException(503, "DEPENDENCY_UNAVAILABLE", "相关服务暂不可用，请稍后重试");
    }
}
