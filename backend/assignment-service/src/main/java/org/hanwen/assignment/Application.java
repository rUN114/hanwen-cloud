package org.hanwen.assignment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"org.hanwen.common", "org.hanwen.assignment"})
@EnableFeignClients(basePackages = {"org.hanwen.common", "org.hanwen.assignment"})
@MapperScan("org.hanwen.assignment.mapper")
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
