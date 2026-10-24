package org.hanwen.assignment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"org.hanwen.common", "org.hanwen.assignment"})
@EnableFeignClients(basePackages = {"org.hanwen.common", "org.hanwen.assignment"})
@MapperScan("org.hanwen.assignment.mapper")
@EnableScheduling
public class Application {
    static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
