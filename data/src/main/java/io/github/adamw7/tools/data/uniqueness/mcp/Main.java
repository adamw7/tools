package io.github.adamw7.tools.data.uniqueness.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        System.setProperty("transport.mode", "stdio");
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("banner-mode", "off");
        SpringApplication.run(Main.class, args);
    }

}
