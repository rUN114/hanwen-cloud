package org.hanwen.assignment;

import org.hanwen.common.*;
import org.springframework.context.annotation.*;

@Configuration
public class AuthConfiguration {
    @Bean
    AuthLookup authLookup(InternalUserClient c, Remote r) {
        return id -> r.call("auth-state", () -> c.auth(id));
    }
}
