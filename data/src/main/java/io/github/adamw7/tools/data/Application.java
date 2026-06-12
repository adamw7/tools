package io.github.adamw7.tools.data;

import io.github.adamw7.tools.data.network.TlsEnforcer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

	public static void main(String[] args) {
		TlsEnforcer.enforce();
		SpringApplication.run(Application.class, args);
	}

}
