package org.hanwen.learning.config;

import org.hanwen.common.*;
import org.springframework.context.annotation.*;

@Configuration
public class AuthConfiguration {
    @Bean
    AuthLookup authLookup(InternalUserClient c, Remote r) {
        return AuthLookup.remote(c, r);
    }
}
