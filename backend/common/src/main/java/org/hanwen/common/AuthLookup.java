package org.hanwen.common;

public interface AuthLookup {
    Contracts.AuthState find(long id);
}
