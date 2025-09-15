package org.openjproxy.grpc.server;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify the new configuration property is loaded correctly.
 */
public class ServerConfigurationUpdateGlobalAvgIntervalTest {

    @Test
    public void testDefaultUpdateGlobalAvgInterval() {
        ServerConfiguration config = new ServerConfiguration();
        
        // Test default value
        assertEquals(300L, config.getSlowQueryUpdateGlobalAvgInterval());
    }

    @Test
    public void testCustomUpdateGlobalAvgInterval() {
        // Set a custom value via system property
        System.setProperty("ojp.server.slowQuerySegregation.updateGlobalAvgInterval", "600");
        
        try {
            ServerConfiguration config = new ServerConfiguration();
            assertEquals(600L, config.getSlowQueryUpdateGlobalAvgInterval());
        } finally {
            // Clean up system property
            System.clearProperty("ojp.server.slowQuerySegregation.updateGlobalAvgInterval");
        }
    }

    @Test
    public void testZeroUpdateGlobalAvgInterval() {
        // Set to 0 (always update behavior)
        System.setProperty("ojp.server.slowQuerySegregation.updateGlobalAvgInterval", "0");
        
        try {
            ServerConfiguration config = new ServerConfiguration();
            assertEquals(0L, config.getSlowQueryUpdateGlobalAvgInterval());
        } finally {
            // Clean up system property
            System.clearProperty("ojp.server.slowQuerySegregation.updateGlobalAvgInterval");
        }
    }
}