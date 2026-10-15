package org.hanwen.gateway;

import org.hanwen.common.JwtCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootApplication
public class Application {
    public static void main(String[] a) {
        SpringApplication.run(Application.class, a);
    }

    @Bean
    JwtCodec jwtCodec(@Value("${hw.public-key-file}") String p) throws Exception {
        return new JwtCodec(p);
    }

    @Bean
    @LoadBalanced
    WebClient.Builder serviceWebClient() {
        return WebClient.builder();
    }
}
