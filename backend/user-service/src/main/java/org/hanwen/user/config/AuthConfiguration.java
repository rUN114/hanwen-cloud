package org.hanwen.user.config;

import org.hanwen.common.*;
import org.hanwen.user.mapper.UserMapper;
import org.springframework.context.annotation.*;

@Configuration
public class AuthConfiguration {
    @Bean
    AuthLookup authLookup(UserMapper m) {
        return id -> {
            var u = m.selectById(id);
            return u == null
                    ? null
                    : new Contracts.AuthState(u.id, u.role, u.status, u.authVersion);
        };
    }
}
