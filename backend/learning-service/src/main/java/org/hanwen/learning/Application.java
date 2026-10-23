package org.hanwen.learning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"org.hanwen.common", "org.hanwen.learning"})
@EnableFeignClients(basePackages = {"org.hanwen.common", "org.hanwen.learning"})
@MapperScan("org.hanwen.learning.mapper")
public class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
