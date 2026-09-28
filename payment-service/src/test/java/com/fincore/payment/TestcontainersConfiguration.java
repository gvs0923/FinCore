package com.fincore.payment;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.Socket;
import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @SuppressWarnings({"resource", "rawtypes"})
    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        PostgreSQLContainer container = (PostgreSQLContainer) new PostgreSQLContainer(
                DockerImageName.parse("postgres:15-alpine"))
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2))
                .withStartupTimeout(Duration.ofSeconds(120));
        container.start();
        // Colima port-forwarding delay: wait until the host can actually reach the mapped port
        waitForPort("localhost", container.getFirstMappedPort(), Duration.ofSeconds(30));
        return container;
    }

    private static void waitForPort(String host, int port, Duration timeout) {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            try (Socket s = new Socket(host, port)) {
                return;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
        throw new IllegalStateException("Port " + port + " not reachable after " + timeout);
    }
}