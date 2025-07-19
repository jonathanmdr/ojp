package org.openjdbcproxy.grpc.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ServerConfiguration class.
 */
public class ServerConfigurationTest {

    @AfterEach
    public void cleanup() {
        // Clear any system properties set during tests
        System.clearProperty("ojp.server.port");
        System.clearProperty("ojp.prometheus.port");
        System.clearProperty("ojp.opentelemetry.enabled");
        System.clearProperty("ojp.opentelemetry.endpoint");
        System.clearProperty("ojp.server.threadPoolSize");
        System.clearProperty("ojp.server.maxRequestSize");
        System.clearProperty("ojp.server.logLevel");
        System.clearProperty("ojp.server.accessLogging");
        System.clearProperty("ojp.server.allowedIps");
        System.clearProperty("ojp.server.connectionIdleTimeout");
        System.clearProperty("ojp.prometheus.allowedIps");
    }

    @Test
    public void testDefaultConfiguration() {
        ServerConfiguration config = new ServerConfiguration();

        assertEquals(ServerConfiguration.DEFAULT_SERVER_PORT, config.getServerPort());
        assertEquals(ServerConfiguration.DEFAULT_PROMETHEUS_PORT, config.getPrometheusPort());
        assertEquals(ServerConfiguration.DEFAULT_OPENTELEMETRY_ENABLED, config.isOpenTelemetryEnabled());
        assertEquals(ServerConfiguration.DEFAULT_OPENTELEMETRY_ENDPOINT, config.getOpenTelemetryEndpoint());
        assertEquals(ServerConfiguration.DEFAULT_THREAD_POOL_SIZE, config.getThreadPoolSize());
        assertEquals(ServerConfiguration.DEFAULT_MAX_REQUEST_SIZE, config.getMaxRequestSize());
        assertEquals(ServerConfiguration.DEFAULT_LOG_LEVEL, config.getLogLevel());
        assertEquals(ServerConfiguration.DEFAULT_ACCESS_LOGGING, config.isAccessLogging());
        assertEquals(ServerConfiguration.DEFAULT_ALLOWED_IPS, config.getAllowedIps());
        assertEquals(ServerConfiguration.DEFAULT_CONNECTION_IDLE_TIMEOUT, config.getConnectionIdleTimeout());
        assertEquals(ServerConfiguration.DEFAULT_PROMETHEUS_ALLOWED_IPS, config.getPrometheusAllowedIps());
    }

    @Test
    public void testJvmSystemPropertiesOverride() {
        // Set JVM system properties
        System.setProperty("ojp.server.port", "8080");
        System.setProperty("ojp.prometheus.port", "9091");
        System.setProperty("ojp.opentelemetry.enabled", "false");
        System.setProperty("ojp.opentelemetry.endpoint", "http://localhost:4317");
        System.setProperty("ojp.server.threadPoolSize", "100");
        System.setProperty("ojp.server.maxRequestSize", "8388608"); // 8MB
        System.setProperty("ojp.server.logLevel", "DEBUG");
        System.setProperty("ojp.server.accessLogging", "true");
        System.setProperty("ojp.server.allowedIps", "192.168.1.0/24,10.0.0.1");
        System.setProperty("ojp.server.connectionIdleTimeout", "60000");
        System.setProperty("ojp.prometheus.allowedIps", "127.0.0.1,192.168.1.0/24");

        ServerConfiguration config = new ServerConfiguration();

        assertEquals(8080, config.getServerPort());
        assertEquals(9091, config.getPrometheusPort());
        assertFalse(config.isOpenTelemetryEnabled());
        assertEquals("http://localhost:4317", config.getOpenTelemetryEndpoint());
        assertEquals(100, config.getThreadPoolSize());
        assertEquals(8388608, config.getMaxRequestSize());
        assertEquals("DEBUG", config.getLogLevel());
        assertTrue(config.isAccessLogging());
        assertEquals(List.of("192.168.1.0/24", "10.0.0.1"), config.getAllowedIps());
        assertEquals(60000, config.getConnectionIdleTimeout());
        assertEquals(List.of("127.0.0.1", "192.168.1.0/24"), config.getPrometheusAllowedIps());
    }

    @Test
    public void testInvalidIntegerValues() {
        System.setProperty("ojp.server.port", "invalid");
        System.setProperty("ojp.prometheus.port", "not-a-number");
        System.setProperty("ojp.server.threadPoolSize", "abc");

        ServerConfiguration config = new ServerConfiguration();

        // Should fall back to defaults for invalid values
        assertEquals(ServerConfiguration.DEFAULT_SERVER_PORT, config.getServerPort());
        assertEquals(ServerConfiguration.DEFAULT_PROMETHEUS_PORT, config.getPrometheusPort());
        assertEquals(ServerConfiguration.DEFAULT_THREAD_POOL_SIZE, config.getThreadPoolSize());
    }

    @Test
    public void testInvalidLongValues() {
        System.setProperty("ojp.server.connectionIdleTimeout", "invalid-long");

        ServerConfiguration config = new ServerConfiguration();

        // Should fall back to default for invalid values
        assertEquals(ServerConfiguration.DEFAULT_CONNECTION_IDLE_TIMEOUT, config.getConnectionIdleTimeout());
    }

    @Test
    public void testBooleanValues() {
        System.setProperty("ojp.opentelemetry.enabled", "true");
        System.setProperty("ojp.server.accessLogging", "false");

        ServerConfiguration config = new ServerConfiguration();

        assertTrue(config.isOpenTelemetryEnabled());
        assertFalse(config.isAccessLogging());
    }

    @Test
    public void testListProperties() {
        System.setProperty("ojp.server.allowedIps", "192.168.1.1, 10.0.0.0/8 , 172.16.0.1");
        System.setProperty("ojp.prometheus.allowedIps", "127.0.0.1,192.168.0.0/16");

        ServerConfiguration config = new ServerConfiguration();

        assertEquals(List.of("192.168.1.1", "10.0.0.0/8", "172.16.0.1"), config.getAllowedIps());
        assertEquals(List.of("127.0.0.1", "192.168.0.0/16"), config.getPrometheusAllowedIps());
    }

    @Test
    public void testEmptyListProperties() {
        System.setProperty("ojp.server.allowedIps", "");
        System.setProperty("ojp.prometheus.allowedIps", " ");

        ServerConfiguration config = new ServerConfiguration();

        assertEquals(ServerConfiguration.DEFAULT_ALLOWED_IPS, config.getAllowedIps());
        assertEquals(ServerConfiguration.DEFAULT_PROMETHEUS_ALLOWED_IPS, config.getPrometheusAllowedIps());
    }

    @Test
    public void testGettersReturnDefensiveCopies() {
        ServerConfiguration config = new ServerConfiguration();

        List<String> allowedIps = config.getAllowedIps();
        List<String> prometheusAllowedIps = config.getPrometheusAllowedIps();

        // Modifying returned lists should not affect the configuration
        allowedIps.clear();
        prometheusAllowedIps.clear();

        assertFalse(config.getAllowedIps().isEmpty());
        assertFalse(config.getPrometheusAllowedIps().isEmpty());
    }
}