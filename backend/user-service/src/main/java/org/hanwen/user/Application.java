package org.hanwen.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"org.hanwen.common", "org.hanwen.user"})
@EnableFeignClients(basePackages = {"org.hanwen.common", "org.hanwen.user"})
@MapperScan("org.hanwen.user.mapper")
public class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
