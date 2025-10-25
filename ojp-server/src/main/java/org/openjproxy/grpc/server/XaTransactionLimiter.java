package org.openjproxy.grpc.server;

import org.openjproxy.constants.CommonConstants;

import java.sql.SQLException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Limits the number of concurrent XA transactions using a fair semaphore.
 * <p>
 * Key properties:
 * <ul>
 *   <li>Instance-scoped (no static shared state).</li>
 *   <li>Fair semaphore to reduce starvation under contention.</li>
 *   <li>Guards against double-release so permits never exceed {@code maxTransactions}.</li>
 *   <li>Accurate metrics for total acquired/rejected and active count.</li>
 * </ul>
 */
public final class XaTransactionLimiter {

    private static final String SQLSTATE_LIMIT_REACHED = "XA001";

    private final Semaphore semaphore;
    private final int maxTransactions;
    private final long acquireTimeoutMs;

    private final AtomicInteger active = new AtomicInteger(0);
    private final AtomicLong totalAcquired = new AtomicLong(0);
    private final AtomicLong totalRejected = new AtomicLong(0);

    /**
     * Create a limiter with a max and default timeout from {@link CommonConstants}.
     */
    public XaTransactionLimiter(int maxTransactions) {
        this(maxTransactions, getDefaultTimeoutFromConstants());
    }

    /**
     * Create a limiter with a max and an explicit acquire timeout (milliseconds).
     */
    public XaTransactionLimiter(int maxTransactions, long acquireTimeoutMs) {
        if (maxTransactions <= 0) {
            throw new IllegalArgumentException("maxTransactions must be > 0");
        }
        if (acquireTimeoutMs < 0) {
            throw new IllegalArgumentException("acquireTimeoutMs must be >= 0");
        }
        this.maxTransactions = maxTransactions;
        this.acquireTimeoutMs = acquireTimeoutMs;
        // Use a fair semaphore to keep acquisition order predictable under load.
        this.semaphore = new Semaphore(maxTransactions, true);
    }

    /**
     * Acquire a permit, waiting up to the configured timeout.
     *
     * @throws SQLException when the limit is reached and the acquire times out,
     *                      or the thread is interrupted while waiting.
     */
    public void acquire() throws SQLException {
        final boolean acquired;
        try {
            if (acquireTimeoutMs == 0L) {
                acquired = semaphore.tryAcquire(); // non-blocking
            } else {
                acquired = semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            totalRejected.incrementAndGet();
            throw new SQLException("Interrupted while acquiring XA transaction permit", SQLSTATE_LIMIT_REACHED, ie);
        }

        if (!acquired) {
            totalRejected.incrementAndGet();
            throw new SQLException(
                    "XA transaction limit reached (max=" + maxTransactions + ", timeout=" + acquireTimeoutMs + "ms)",
                    SQLSTATE_LIMIT_REACHED
            );
        }

        // Bookkeeping after a successful acquire.
        active.incrementAndGet();
        totalAcquired.incrementAndGet();
    }

    /**
     * Release a permit. Extra releases are ignored and never increase the semaphore
     * beyond {@code maxTransactions}.
     */
    public void release() {
        // Only release if we actually hold an active permit.
        int current = active.get();
        if (current > 0) {
            // First, reduce the active count.
            active.decrementAndGet();
            // Then, return a permit to the semaphore, but clamp to the configured maximum.
            // This prevents over-release from inflating availablePermits beyond max.
            if (semaphore.availablePermits() < maxTransactions) {
                semaphore.release();
            }
        } else {
            // Ignore double/extra releases; optionally log if you have a logger.
            // e.g., log.warn("Extra release() call ignored");
        }
    }

    // ----- Metrics & Introspection -----

    public int getMaxTransactions() {
        return maxTransactions;
    }

    /** Number of permits currently held (concurrent active transactions). */
    public int getActiveTransactions() {
        return active.get();
    }

    /** Number of permits currently available to acquire. */
    public int getAvailablePermits() {
        // Since we clamp releases, this will always be in [0, maxTransactions].
        return semaphore.availablePermits();
    }

    /** Total successful acquire attempts since construction. */
    public long getTotalAcquired() {
        return totalAcquired.get();
    }

    /** Total rejected (timed-out or interrupted) acquire attempts since construction. */
    public long getTotalRejected() {
        return totalRejected.get();
    }

    @Override
    public String toString() {
        return "XaTransactionLimiter{" +
                "max=" + maxTransactions +
                ", active=" + active.get() +
                ", availablePermits=" + semaphore.availablePermits() +
                ", totalAcquired=" + totalAcquired.get() +
                ", totalRejected=" + totalRejected.get() +
                ", timeoutMs=" + acquireTimeoutMs +
                '}';
    }

    // ----- Helpers -----

    private static long getDefaultTimeoutFromConstants() {
        // Keep this adapter minimal so tests don't depend on a specific constant name.
        // Replace with your actual constant if different.
        return CommonConstants.DEFAULT_XA_START_TIMEOUT_MILLIS;
    }
}
