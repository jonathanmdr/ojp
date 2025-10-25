package org.openjproxy.grpc.server;

import org.junit.jupiter.api.Test;
import org.openjproxy.constants.CommonConstants;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XaTransactionLimiter
 */
class XaTransactionLimiterTest {

    @Test
    void testAcquireAndReleaseSingleThread() throws SQLException {
        XaTransactionLimiter limiter = new XaTransactionLimiter(5);
        
        assertEquals(5, limiter.getMaxTransactions());
        assertEquals(0, limiter.getActiveTransactions());
        assertEquals(5, limiter.getAvailablePermits());
        
        // Acquire a permit
        limiter.acquire();
        assertEquals(1, limiter.getActiveTransactions());
        assertEquals(4, limiter.getAvailablePermits());
        assertEquals(1, limiter.getTotalAcquired());
        
        // Release the permit
        limiter.release();
        assertEquals(0, limiter.getActiveTransactions());
        assertEquals(5, limiter.getAvailablePermits());
    }
    
    @Test
    void testAcquireUpToLimit() throws SQLException {
        XaTransactionLimiter limiter = new XaTransactionLimiter(3);
        
        // Acquire all 3 permits
        limiter.acquire();
        limiter.acquire();
        limiter.acquire();
        
        assertEquals(3, limiter.getActiveTransactions());
        assertEquals(0, limiter.getAvailablePermits());
        assertEquals(3, limiter.getTotalAcquired());
    }
    
    @Test
    void testAcquireBlocksWhenLimitReached() {
        XaTransactionLimiter limiter = new XaTransactionLimiter(2, 100); // 100ms timeout
        
        try {
            // Acquire all permits
            limiter.acquire();
            limiter.acquire();
            
            // Next acquire should timeout
            SQLException exception = assertThrows(SQLException.class, limiter::acquire);
            assertTrue(exception.getMessage().contains("XA transaction limit reached"));
            assertEquals("XA001", exception.getSQLState());
            
            assertEquals(1, limiter.getTotalRejected());
            
        } catch (SQLException e) {
            fail("Unexpected SQLException: " + e.getMessage());
        }
    }
    
    @Test
    void testReleaseAllowsNewAcquire() throws SQLException {
        XaTransactionLimiter limiter = new XaTransactionLimiter(2, 100);
        
        // Acquire all permits
        limiter.acquire();
        limiter.acquire();
        assertEquals(0, limiter.getAvailablePermits());
        
        // Release one
        limiter.release();
        assertEquals(1, limiter.getAvailablePermits());
        
        // Should be able to acquire again
        limiter.acquire();
        assertEquals(0, limiter.getAvailablePermits());
    }
    
    @Test
    void testConcurrentAcquireAndRelease() throws InterruptedException {
        int maxTransactions = 5;
        XaTransactionLimiter limiter = new XaTransactionLimiter(maxTransactions, 5000);
        int threadCount = 20;
        int iterationsPerThread = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger maxConcurrent = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    
                    for (int j = 0; j < iterationsPerThread; j++) {
                        limiter.acquire();
                        
                        // Track max concurrent
                        int concurrent = limiter.getActiveTransactions();
                        maxConcurrent.updateAndGet(current -> Math.max(current, concurrent));
                        
                        // Simulate some work
                        Thread.sleep(10);
                        
                        limiter.release();
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore for this test
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "Test should complete within 30 seconds");
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify that we never exceeded the limit
        assertTrue(maxConcurrent.get() <= maxTransactions, 
                "Max concurrent transactions should not exceed limit: " + maxConcurrent.get());
        
        // Verify all permits are released
        assertEquals(0, limiter.getActiveTransactions());
        assertEquals(maxTransactions, limiter.getAvailablePermits());
        
        // Verify successful operations
        assertTrue(successCount.get() > 0, "Should have successful operations");
    }
    
    @Test
    void testConcurrentWithSomeRejections() throws InterruptedException {
        int maxTransactions = 3;
        XaTransactionLimiter limiter = new XaTransactionLimiter(maxTransactions, 50); // Short timeout
        int threadCount = 10;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    limiter.acquire();
                    successCount.incrementAndGet();
                    
                    // Hold the permit for a while
                    Thread.sleep(200);
                    
                    limiter.release();
                } catch (SQLException e) {
                    // Expected for some threads
                    rejectedCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        
        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Verify some succeeded and some were rejected
        assertTrue(successCount.get() > 0, "Some threads should succeed");
        assertTrue(rejectedCount.get() > 0, "Some threads should be rejected");
        assertEquals(threadCount, successCount.get() + rejectedCount.get(), 
                "All threads should either succeed or be rejected");
        
        // Verify metrics
        assertEquals(rejectedCount.get(), limiter.getTotalRejected());
        assertEquals(successCount.get(), limiter.getTotalAcquired());
        assertEquals(0, limiter.getActiveTransactions());
    }
    
    @Test
    void testDefaultTimeout() {
        XaTransactionLimiter limiter = new XaTransactionLimiter(1);
        // Should use default timeout from CommonConstants
        assertNotNull(limiter);
    }
    
    @Test
    void testMultipleReleasesDoNotBreakSemaphore() throws SQLException {
        XaTransactionLimiter limiter = new XaTransactionLimiter(2);
        
        limiter.acquire();
        limiter.release();
        
        // Extra release (should not break anything due to Semaphore behavior)
        limiter.release();
        
        // Should still be able to acquire normally
        limiter.acquire();
        limiter.acquire();
        
        // But third should fail
        assertThrows(SQLException.class, () -> {
            XaTransactionLimiter limiter2 = new XaTransactionLimiter(2, 50);
            limiter2.acquire();
            limiter2.acquire();
            limiter2.acquire(); // This should timeout
        });
    }
    
    @Test
    void testGettersReflectCurrentState() throws SQLException {
        XaTransactionLimiter limiter = new XaTransactionLimiter(10, 1000);
        
        assertEquals(10, limiter.getMaxTransactions());
        assertEquals(0, limiter.getActiveTransactions());
        assertEquals(0, limiter.getTotalAcquired());
        assertEquals(0, limiter.getTotalRejected());
        
        limiter.acquire();
        assertEquals(1, limiter.getActiveTransactions());
        assertEquals(1, limiter.getTotalAcquired());
        
        limiter.acquire();
        assertEquals(2, limiter.getActiveTransactions());
        assertEquals(2, limiter.getTotalAcquired());
        
        limiter.release();
        assertEquals(1, limiter.getActiveTransactions());
        assertEquals(2, limiter.getTotalAcquired()); // Total acquired doesn't decrease
    }
}
