package org.openjproxy.grpc.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.constants.CommonConstants;

import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages concurrent XA transaction limits using a Semaphore.
 * Enforces a maximum number of active XA transactions at any given time.
 */
@Slf4j
public class XaTransactionLimiter {
    
    private final Semaphore semaphore;
    @Getter
    private final int maxTransactions;
    private final long startTimeoutMillis;
    private final AtomicInteger activeTransactions = new AtomicInteger(0);
    private final AtomicInteger totalAcquired = new AtomicInteger(0);
    private final AtomicInteger totalRejected = new AtomicInteger(0);
    
    /**
     * Creates a new XA transaction limiter.
     * 
     * @param maxTransactions Maximum number of concurrent XA transactions
     * @param startTimeoutMillis Timeout in milliseconds for acquiring a transaction slot (default: 60000)
     */
    public XaTransactionLimiter(int maxTransactions, long startTimeoutMillis) {
        this.maxTransactions = maxTransactions;
        this.startTimeoutMillis = startTimeoutMillis;
        this.semaphore = new Semaphore(maxTransactions, true); // fair semaphore
        log.info("XaTransactionLimiter initialized with maxTransactions={}, startTimeout={}ms", 
                maxTransactions, startTimeoutMillis);
    }
    
    /**
     * Creates a new XA transaction limiter with default timeout.
     * 
     * @param maxTransactions Maximum number of concurrent XA transactions
     */
    public XaTransactionLimiter(int maxTransactions) {
        this(maxTransactions, CommonConstants.DEFAULT_XA_START_TIMEOUT_MILLIS);
    }
    
    /**
     * Acquires a permit to start an XA transaction.
     * Blocks up to the configured timeout if no permits are available.
     * 
     * @throws SQLException if unable to acquire a permit within the timeout
     */
    public void acquire() throws SQLException {
        try {
            boolean acquired = semaphore.tryAcquire(startTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                totalRejected.incrementAndGet();
                log.warn("XA transaction limit reached. Max: {}, Active: {}, Timeout after {}ms", 
                        maxTransactions, activeTransactions.get(), startTimeoutMillis);
                throw new SQLException(
                    String.format("XA transaction limit reached. Maximum %d concurrent XA transactions allowed. " +
                                "Unable to acquire slot after %dms timeout.", 
                                maxTransactions, startTimeoutMillis),
                    "XA001"  // Custom SQL state for XA limit reached
                );
            }
            activeTransactions.incrementAndGet();
            totalAcquired.incrementAndGet();
            log.debug("XA transaction permit acquired. Active: {}/{}", activeTransactions.get(), maxTransactions);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            totalRejected.incrementAndGet();
            log.error("Interrupted while waiting for XA transaction permit", e);
            throw new SQLException("Interrupted while waiting for XA transaction permit", "XA002", e);
        }
    }
    
    /**
     * Releases a permit after an XA transaction ends.
     * Should always be called in a finally block to ensure permit is released.
     */
    public void release() {
        semaphore.release();
        int active = activeTransactions.decrementAndGet();
        log.debug("XA transaction permit released. Active: {}/{}", active, maxTransactions);
    }
    
    /**
     * Gets the current number of active XA transactions.
     * 
     * @return Number of currently active XA transactions
     */
    public int getActiveTransactions() {
        return activeTransactions.get();
    }
    
    /**
     * Gets the total number of successfully acquired XA transaction permits.
     * 
     * @return Total acquired count
     */
    public int getTotalAcquired() {
        return totalAcquired.get();
    }
    
    /**
     * Gets the total number of rejected XA transaction attempts (timeouts).
     * 
     * @return Total rejected count
     */
    public int getTotalRejected() {
        return totalRejected.get();
    }
    
    /**
     * Gets the number of available permits.
     * 
     * @return Number of available XA transaction slots
     */
    public int getAvailablePermits() {
        return semaphore.availablePermits();
    }
}
