package org.hanwen.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
public class InstanceController {
    @Value("${spring.application.name}")
    String name;

    @GetMapping("/internal/instance")
    public Object instance(HttpServletRequest r) {
        return Views.map(
                "serviceName",
                name,
                "instanceId",
                name + ":" + r.getLocalPort(),
                "port",
                r.getLocalPort(),
                "traceId",
                Trace.id());
    }
}
