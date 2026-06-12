package io.github.adamw7.tools.data.network;

public class TlsEnforcer {

    static final String TLS_1_3 = "TLSv1.3";

    private TlsEnforcer() {}

    public static void enforce() {
        System.setProperty("jdk.tls.client.protocols", TLS_1_3);
        System.setProperty("jdk.tls.server.protocols", TLS_1_3);
        System.setProperty("https.protocols", TLS_1_3);
    }
}
