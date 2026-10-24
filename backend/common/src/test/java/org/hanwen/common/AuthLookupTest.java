package org.hanwen.common;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

class AuthLookupTest {
    @Test
    void missingAccountIsAbsentButUnavailableAccountServiceIsNot() {
        var client = mock(InternalUserClient.class);
        var lookup = AuthLookup.remote(client, new Remote());
        when(client.auth(1)).thenThrow(ApiException.missing());
        assertNull(lookup.find(1));
        var unavailable = ApiException.unavailable();
        when(client.auth(2)).thenThrow(unavailable);
        assertSame(unavailable, assertThrows(ApiException.class, () -> lookup.find(2)));
    }

    @Test
    void validStatePassesThroughUnchanged() {
        var client = mock(InternalUserClient.class);
        var state = new Contracts.AuthState(1, "STUDENT", "ENABLED", 2);
        when(client.auth(1)).thenReturn(state);
        assertSame(state, AuthLookup.remote(client, new Remote()).find(1));
    }
}
