package org.hanwen.common;

import feign.*;
import feign.codec.ErrorDecoder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.context.request.*;

@Configuration
public class FeignConfiguration {
    @Bean
    RequestInterceptor headers(@Value("${hw.service-key}") String key) {
        return t -> {
            t.header("X-Service-Key", key);
            t.header("X-Trace-Id", Trace.id());
            var a = RequestContextHolder.getRequestAttributes();
            if (a instanceof ServletRequestAttributes s) {
                String auth = s.getRequest().getHeader("Authorization");
                if (auth != null) t.header("Authorization", auth);
            }
        };
    }

    @Bean
    ErrorDecoder decoder() {
        return (method, r) ->
                switch (r.status()) {
                    case 400 -> ApiException.bad("关联资源参数不正确");
                    case 401 -> new ApiException(401, "AUTH_INVALID", "请重新登录");
                    case 403 -> ApiException.forbidden();
                    case 404 -> ApiException.missing();
                    case 409 -> ApiException.conflict("关联资源状态已改变");
                    default -> ApiException.unavailable();
                };
    }
}
