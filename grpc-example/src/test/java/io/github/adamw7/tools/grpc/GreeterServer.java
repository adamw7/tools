package io.github.adamw7.tools.grpc;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;

/**
 * Runnable Netty-backed gRPC server hosting {@link GreeterServiceImpl}.
 * Start it with {@code mvn -pl grpc-example exec:java} or from an IDE, then
 * drive it with {@link GreeterClient}.
 */
public class GreeterServer {

	private static final Logger log = LogManager.getLogger(GreeterServer.class.getName());
	private static final int DEFAULT_PORT = 50051;

	private final Server server;

	public GreeterServer(int port) {
		server = Grpc.newServerBuilderForPort(port, InsecureServerCredentials.create())
				.addService(new GreeterServiceImpl()).build();
	}

	public void start() throws IOException {
		server.start();
		log.info("Greeter server listening on port {}", server.getPort());
		Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
	}

	public void stop() {
		log.info("Shutting down Greeter server");
		server.shutdown();
	}

	public void awaitTermination() throws InterruptedException {
		server.awaitTermination();
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		GreeterServer server = new GreeterServer(DEFAULT_PORT);
		server.start();
		server.awaitTermination();
	}
}
