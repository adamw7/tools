package io.github.adamw7.tools.data.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TlsEnforcerTest {

    @Test
    public void enforce_setsTls13SystemProperties() {
        TlsEnforcer.enforce();
        assertEquals(TlsEnforcer.TLS_1_3, System.getProperty("jdk.tls.client.protocols"));
        assertEquals(TlsEnforcer.TLS_1_3, System.getProperty("jdk.tls.server.protocols"));
        assertEquals(TlsEnforcer.TLS_1_3, System.getProperty("https.protocols"));
    }
}
