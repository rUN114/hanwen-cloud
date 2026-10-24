package org.hanwen.common;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "hw-user-service", contextId = "auth-state")
public interface InternalUserClient {
    @GetMapping("/internal/users/{id}/auth-state")
    Contracts.AuthState auth(@PathVariable long id);
}
