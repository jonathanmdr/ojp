package org.openjdbcproxy.grpc.server;

import org.openjdbcproxy.constants.CommonConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for the OJP Server that loads settings from JVM arguments and environment variables.
 * JVM arguments take precedence over environment variables.
 */
public class ServerConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(ServerConfiguration.class);

    // Configuration keys
    private static final String SERVER_PORT_KEY = "ojp.server.port";
    private static final String PROMETHEUS_PORT_KEY = "ojp.prometheus.port";
    private static final String OPENTELEMETRY_ENABLED_KEY = "ojp.opentelemetry.enabled";
    private static final String OPENTELEMETRY_ENDPOINT_KEY = "ojp.opentelemetry.endpoint";
    private static final String THREAD_POOL_SIZE_KEY = "ojp.server.threadPoolSize";
    private static final String MAX_REQUEST_SIZE_KEY = "ojp.server.maxRequestSize";
    private static final String LOG_LEVEL_KEY = "ojp.server.logLevel";
    private static final String ACCESS_LOGGING_KEY = "ojp.server.accessLogging";
    private static final String ALLOWED_IPS_KEY = "ojp.server.allowedIps";
    private static final String CONNECTION_IDLE_TIMEOUT_KEY = "ojp.server.connectionIdleTimeout";
    private static final String PROMETHEUS_ALLOWED_IPS_KEY = "ojp.prometheus.allowedIps";

    // Default values
    public static final int DEFAULT_SERVER_PORT = CommonConstants.DEFAULT_PORT_NUMBER;
    public static final int DEFAULT_PROMETHEUS_PORT = 9090;
    public static final boolean DEFAULT_OPENTELEMETRY_ENABLED = true;
    public static final String DEFAULT_OPENTELEMETRY_ENDPOINT = "";
    public static final int DEFAULT_THREAD_POOL_SIZE = 200;
    public static final int DEFAULT_MAX_REQUEST_SIZE = 4 * 1024 * 1024; // 4MB
    public static final String DEFAULT_LOG_LEVEL = "INFO";
    public static final boolean DEFAULT_ACCESS_LOGGING = false;
    public static final List<String> DEFAULT_ALLOWED_IPS = List.of("0.0.0.0/0"); // Allow all by default
    public static final long DEFAULT_CONNECTION_IDLE_TIMEOUT = 30000; // 30 seconds
    public static final List<String> DEFAULT_PROMETHEUS_ALLOWED_IPS = List.of("0.0.0.0/0"); // Allow all by default

    // Configuration values
    private final int serverPort;
    private final int prometheusPort;
    private final boolean openTelemetryEnabled;
    private final String openTelemetryEndpoint;
    private final int threadPoolSize;
    private final int maxRequestSize;
    private final String logLevel;
    private final boolean accessLogging;
    private final List<String> allowedIps;
    private final long connectionIdleTimeout;
    private final List<String> prometheusAllowedIps;

    public ServerConfiguration() {
        this.serverPort = getIntProperty(SERVER_PORT_KEY, DEFAULT_SERVER_PORT);
        this.prometheusPort = getIntProperty(PROMETHEUS_PORT_KEY, DEFAULT_PROMETHEUS_PORT);
        this.openTelemetryEnabled = getBooleanProperty(OPENTELEMETRY_ENABLED_KEY, DEFAULT_OPENTELEMETRY_ENABLED);
        this.openTelemetryEndpoint = getStringProperty(OPENTELEMETRY_ENDPOINT_KEY, DEFAULT_OPENTELEMETRY_ENDPOINT);
        this.threadPoolSize = getIntProperty(THREAD_POOL_SIZE_KEY, DEFAULT_THREAD_POOL_SIZE);
        this.maxRequestSize = getIntProperty(MAX_REQUEST_SIZE_KEY, DEFAULT_MAX_REQUEST_SIZE);
        this.logLevel = getStringProperty(LOG_LEVEL_KEY, DEFAULT_LOG_LEVEL);
        this.accessLogging = getBooleanProperty(ACCESS_LOGGING_KEY, DEFAULT_ACCESS_LOGGING);
        this.allowedIps = getListProperty(ALLOWED_IPS_KEY, DEFAULT_ALLOWED_IPS);
        this.connectionIdleTimeout = getLongProperty(CONNECTION_IDLE_TIMEOUT_KEY, DEFAULT_CONNECTION_IDLE_TIMEOUT);
        this.prometheusAllowedIps = getListProperty(PROMETHEUS_ALLOWED_IPS_KEY, DEFAULT_PROMETHEUS_ALLOWED_IPS);

        logConfigurationSummary();
    }

    /**
     * Gets a string property value. JVM system properties take precedence over environment variables.
     */
    private String getStringProperty(String key, String defaultValue) {
        // First check JVM system properties
        String value = System.getProperty(key);
        if (value != null) {
            logger.debug("Using JVM property {}={}", key, value);
            return value;
        }

        // Then check environment variables (convert dots to underscores and uppercase)
        String envKey = key.replace('.', '_').toUpperCase();
        value = System.getenv(envKey);
        if (value != null) {
            logger.debug("Using environment variable {}={}", envKey, value);
            return value;
        }

        logger.debug("Using default value for {}: {}", key, defaultValue);
        return defaultValue;
    }

    /**
     * Gets an integer property value with validation.
     */
    private int getIntProperty(String key, int defaultValue) {
        String value = getStringProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for property '{}': {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Gets a long property value with validation.
     */
    private long getLongProperty(String key, long defaultValue) {
        String value = getStringProperty(key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid long value for property '{}': {}, using default: {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Gets a boolean property value.
     */
    private boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getStringProperty(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets a list property value (comma-separated).
     */
    private List<String> getListProperty(String key, List<String> defaultValue) {
        String value = getStringProperty(key, String.join(",", defaultValue));
        if (value.trim().isEmpty()) {
            return new ArrayList<>(defaultValue);
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * Logs a summary of the current configuration.
     */
    private void logConfigurationSummary() {
        logger.info("OJP Server Configuration:");
        logger.info("  Server Port: {}", serverPort);
        logger.info("  Prometheus Port: {}", prometheusPort);
        logger.info("  OpenTelemetry Enabled: {}", openTelemetryEnabled);
        logger.info("  OpenTelemetry Endpoint: {}", openTelemetryEndpoint.isEmpty() ? "default" : openTelemetryEndpoint);
        logger.info("  Thread Pool Size: {}", threadPoolSize);
        logger.info("  Max Request Size: {} bytes", maxRequestSize);
        logger.info("  Log Level: {}", logLevel);
        logger.info("  Access Logging: {}", accessLogging);
        logger.info("  Allowed IPs: {}", allowedIps);
        logger.info("  Connection Idle Timeout: {} ms", connectionIdleTimeout);
        logger.info("  Prometheus Allowed IPs: {}", prometheusAllowedIps);
    }

    // Getters
    public int getServerPort() {
        return serverPort;
    }

    public int getPrometheusPort() {
        return prometheusPort;
    }

    public boolean isOpenTelemetryEnabled() {
        return openTelemetryEnabled;
    }

    public String getOpenTelemetryEndpoint() {
        return openTelemetryEndpoint;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public int getMaxRequestSize() {
        return maxRequestSize;
    }

    public String getLogLevel() {
        return logLevel;
    }

    public boolean isAccessLogging() {
        return accessLogging;
    }

    public List<String> getAllowedIps() {
        return new ArrayList<>(allowedIps);
    }

    public long getConnectionIdleTimeout() {
        return connectionIdleTimeout;
    }

    public List<String> getPrometheusAllowedIps() {
        return new ArrayList<>(prometheusAllowedIps);
    }
}