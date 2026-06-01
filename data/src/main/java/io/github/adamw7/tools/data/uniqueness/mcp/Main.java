package io.github.adamw7.tools.data.uniqueness.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        String transportMode = resolveTransportMode(args);
        System.setProperty("transport.mode", transportMode);
        if ("stdio".equals(transportMode)) {
            System.setProperty("spring.main.web-application-type", "none");
        }
        System.setProperty("banner-mode", "off");
        SpringApplication.run(Main.class, args);
    }

    private static String resolveTransportMode(String[] args) {
        String prefix = "--transport.mode=";
        for (String arg : args) {
            if (arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return "stdio";
    }

}
