package org.openjproxy.grpc.server.pool;

import com.atomikos.icatch.config.UserTransactionService;
import com.atomikos.icatch.config.UserTransactionServiceImp;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.constants.CommonConstants;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of Atomikos UserTransactionService.
 * Handles startup, configuration, and shutdown of the XA transaction infrastructure.
 */
@Slf4j
public class AtomikosLifecycle {
    
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean started = new AtomicBoolean(false);
    
    @Getter
    private static UserTransactionService userTransactionService;
    
    /**
     * Initializes and starts the Atomikos transaction manager.
     * This should be called once at server startup when XA support is needed.
     * 
     * @param loggingEnabled Whether to enable Atomikos transaction logging
     * @param loggingDir Directory for transaction logs (used if logging is enabled)
     */
    public static synchronized void start(boolean loggingEnabled, String loggingDir) {
        if (started.get()) {
            log.warn("AtomikosLifecycle already started, ignoring duplicate start request");
            return;
        }
        
        try {
            log.info("Starting Atomikos transaction manager...");
            
            // Configure Atomikos properties
            Properties atomikosProperties = new Properties();
            
            // Set service name
            atomikosProperties.setProperty("com.atomikos.icatch.service", "com.atomikos.icatch.standalone.UserTransactionServiceFactory");
            
            // Configure transaction logging
            if (loggingEnabled) {
                // Ensure logging directory exists
                Path logPath = Paths.get(loggingDir);
                if (!Files.exists(logPath)) {
                    Files.createDirectories(logPath);
                    log.info("Created Atomikos logging directory: {}", loggingDir);
                }
                
                atomikosProperties.setProperty("com.atomikos.icatch.log_base_dir", loggingDir);
                atomikosProperties.setProperty("com.atomikos.icatch.log_base_name", "tmlog");
                atomikosProperties.setProperty("com.atomikos.icatch.output_dir", loggingDir);
                atomikosProperties.setProperty("com.atomikos.icatch.enable_logging", "true");
                log.info("Atomikos transaction logging enabled in directory: {}", loggingDir);
            } else {
                // Use temp directory for minimal logging configuration
                String tempDir = System.getProperty("java.io.tmpdir");
                atomikosProperties.setProperty("com.atomikos.icatch.log_base_dir", tempDir);
                atomikosProperties.setProperty("com.atomikos.icatch.log_base_name", "tmlog-disabled");
                atomikosProperties.setProperty("com.atomikos.icatch.output_dir", tempDir);
                atomikosProperties.setProperty("com.atomikos.icatch.enable_logging", "false");
                // Set checkpoint interval to a very high value to minimize disk I/O
                atomikosProperties.setProperty("com.atomikos.icatch.checkpoint_interval", "1000000");
                log.info("Atomikos transaction logging disabled, using temp directory for minimal config");
            }
            
            // Set timeout defaults
            atomikosProperties.setProperty("com.atomikos.icatch.default_jta_timeout", "300000"); // 5 minutes in ms
            atomikosProperties.setProperty("com.atomikos.icatch.max_timeout", "600000"); // 10 minutes in ms
            
            // Set recovery settings
            atomikosProperties.setProperty("com.atomikos.icatch.recovery_delay", "10000"); // 10 seconds
            atomikosProperties.setProperty("com.atomikos.icatch.oltp_max_retries", "5");
            atomikosProperties.setProperty("com.atomikos.icatch.oltp_retry_interval", "10000"); // 10 seconds
            
            // Initialize UserTransactionServiceImp with properties
            userTransactionService = new UserTransactionServiceImp(atomikosProperties);
            userTransactionService.init();
            
            initialized.set(true);
            started.set(true);
            
            log.info("Atomikos transaction service started successfully");
            
        } catch (Exception e) {
            log.error("Failed to start Atomikos transaction manager", e);
            throw new RuntimeException("Failed to start Atomikos transaction manager", e);
        }
    }
    
    /**
     * Stops the Atomikos transaction service.
     * This should be called at server shutdown.
     */
    public static synchronized void stop() {
        if (!started.get()) {
            log.debug("AtomikosLifecycle not started, nothing to stop");
            return;
        }
        
        try {
            log.info("Stopping Atomikos transaction service...");
            
            // Shutdown UserTransactionService
            if (userTransactionService != null) {
                try {
                    userTransactionService.shutdown(false); // false = don't force
                    log.debug("UserTransactionService shutdown");
                } catch (Exception e) {
                    log.warn("Error shutting down UserTransactionService", e);
                }
                userTransactionService = null;
            }
            
            started.set(false);
            initialized.set(false);
            
            log.info("Atomikos transaction service stopped successfully");
            
        } catch (Exception e) {
            log.error("Error stopping Atomikos transaction service", e);
        }
    }
    
    /**
     * Checks if Atomikos has been initialized and started.
     * 
     * @return true if started, false otherwise
     */
    public static boolean isStarted() {
        return started.get();
    }
    
    /**
     * Checks if Atomikos has been initialized.
     * 
     * @return true if initialized, false otherwise
     */
    public static boolean isInitialized() {
        return initialized.get();
    }
}
