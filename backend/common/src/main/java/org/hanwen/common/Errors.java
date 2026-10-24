package org.hanwen.common;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class Errors {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Errors.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Object>> api(ApiException e) {
        return ResponseEntity.status(e.status)
                .body(new ApiResponse<>(e.code, e.getMessage(), null, Trace.id()));
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiResponse<Object>> input() {
        return api(ApiException.bad("输入格式错误，或包含不允许的字段"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Object>> upload() {
        return ResponseEntity.status(413)
                .body(new ApiResponse<>("UPLOAD_TOO_LARGE", "文件不能超过20MB", null, Trace.id()));
    }

    @ExceptionHandler({
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.multipart.support.MissingServletRequestPartException.class
    })
    ResponseEntity<ApiResponse<Object>> missingInput() {
        return api(ApiException.bad("缺少必填字段或文件"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiResponse<Object>> duplicate() {
        return api(new ApiException(409, "DUPLICATE", "数据已存在，或不满足完整性约束"));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Object>> notFound() {
        return api(ApiException.missing());
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiResponse<Object>> method(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        String[] methods = e.getSupportedMethods();
        return ResponseEntity.status(405)
                .header(
                        org.springframework.http.HttpHeaders.ALLOW,
                        methods == null ? "" : String.join(", ", methods))
                .body(new ApiResponse<>("METHOD_NOT_ALLOWED", "此接口不支持该请求方法", null, Trace.id()));
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiResponse<Object>> media() {
        return api(new ApiException(415, "UNSUPPORTED_MEDIA_TYPE", "请按接口要求提交文件或JSON数据"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Object>> unexpected(Exception e) {
        LOG.error("traceId={} unexpected", Trace.id(), e);
        return ResponseEntity.status(500)
                .body(new ApiResponse<>("INTERNAL_ERROR", "操作未完成，请联系管理员", null, Trace.id()));
    }
}
