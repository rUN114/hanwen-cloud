package org.hanwen.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class WebConfiguration {
    @Bean
    JwtCodec jwtCodec(@Value("${hw.public-key-file}") String path) throws Exception {
        return new JwtCodec(path);
    }

    @Bean
    FilterRegistrationBean<AuthFilter> authFilter(
            JwtCodec jwt, AuthLookup lookup, @Value("${hw.service-key}") String key) {
        var b = new FilterRegistrationBean<>(new AuthFilter(jwt, lookup, key));
        b.setOrder(-100);
        return b;
    }
}
