package org.hanwen.common;

public interface AuthLookup {
    Contracts.AuthState find(long id);

    static AuthLookup remote(InternalUserClient client, Remote remote) {
        return id -> {
            try {
                return remote.call("auth-state", () -> client.auth(id));
            } catch (ApiException e) {
                // 账号记录不存在交给认证过滤器判失效，依赖故障仍须向上传播。
                if (e.status == 404) return null;
                throw e;
            }
        };
    }
}
