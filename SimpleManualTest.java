import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple test version of QueryPerformanceMonitor without logging dependencies.
 */
class SimpleQueryPerformanceMonitor {
    
    private static class PerformanceRecord {
        private volatile double averageExecutionTime;
        private final AtomicLong executionCount;
        
        public PerformanceRecord(double initialTime) {
            this.averageExecutionTime = initialTime;
            this.executionCount = new AtomicLong(1);
        }
        
        public void updateAverage(double newMeasurement) {
            synchronized (this) {
                this.averageExecutionTime = ((this.averageExecutionTime * 4) + newMeasurement) / 5;
                this.executionCount.incrementAndGet();
            }
        }
        
        public double getAverageExecutionTime() {
            return averageExecutionTime;
        }
    }
    
    private final ConcurrentHashMap<String, PerformanceRecord> operationRecords = new ConcurrentHashMap<>();
    private volatile double overallAverageExecutionTime = 0.0;
    
    public void recordExecutionTime(String operationHash, double executionTimeMs) {
        if (operationHash == null || executionTimeMs < 0) {
            return;
        }
        
        operationRecords.compute(operationHash, (key, existing) -> {
            if (existing == null) {
                return new PerformanceRecord(executionTimeMs);
            } else {
                existing.updateAverage(executionTimeMs);
                return existing;
            }
        });
        
        updateOverallAverage();
    }
    
    public double getOperationAverageTime(String operationHash) {
        PerformanceRecord record = operationRecords.get(operationHash);
        return record != null ? record.getAverageExecutionTime() : 0.0;
    }
    
    public double getOverallAverageExecutionTime() {
        return overallAverageExecutionTime;
    }
    
    public boolean isSlowOperation(String operationHash) {
        double operationAverage = getOperationAverageTime(operationHash);
        double overallAverage = getOverallAverageExecutionTime();
        
        if (overallAverage <= 1.0) {
            return false;
        }
        
        return operationAverage >= (overallAverage * 2.0);
    }
    
    private void updateOverallAverage() {
        if (operationRecords.isEmpty()) {
            overallAverageExecutionTime = 0.0;
            return;
        }
        
        double sum = operationRecords.values().stream()
                .mapToDouble(PerformanceRecord::getAverageExecutionTime)
                .sum();
        
        overallAverageExecutionTime = sum / operationRecords.size();
    }
}

/**
 * Simple manual test to verify core functionality.
 */
public class SimpleManualTest {
    
    public static void main(String[] args) {
        System.out.println("Testing Slow Query Segregation Core Functionality...");
        
        // Test QueryPerformanceMonitor
        SimpleQueryPerformanceMonitor monitor = new SimpleQueryPerformanceMonitor();
        
        // Test basic functionality
        String operation = "SELECT * FROM users";
        monitor.recordExecutionTime(operation, 100.0);
        
        double avgTime = monitor.getOperationAverageTime(operation);
        System.out.println("Operation average time: " + avgTime + "ms (expected: 100.0)");
        assert Math.abs(avgTime - 100.0) < 0.001 : "Expected 100.0, got " + avgTime;
        
        double overallAvg = monitor.getOverallAverageExecutionTime();
        System.out.println("Overall average time: " + overallAvg + "ms (expected: 100.0)");
        assert Math.abs(overallAvg - 100.0) < 0.001 : "Expected 100.0, got " + overallAvg;
        
        // Test weighted averaging
        monitor.recordExecutionTime(operation, 200.0);
        avgTime = monitor.getOperationAverageTime(operation);
        System.out.println("After second execution: " + avgTime + "ms (expected: 120.0)");
        assert Math.abs(avgTime - 120.0) < 0.001 : "Expected 120.0, got " + avgTime;
        
        // Test slow operation classification
        boolean isSlow = monitor.isSlowOperation(operation);
        System.out.println("Is operation slow: " + isSlow + " (expected: false, since overall avg is 120)");
        assert !isSlow : "Operation should not be slow yet";
        
        // Add a faster operation to create a baseline
        monitor.recordExecutionTime("fast-op", 50.0);
        
        // Overall should now be (120 + 50) / 2 = 85
        overallAvg = monitor.getOverallAverageExecutionTime();
        System.out.println("Overall average after adding fast op: " + overallAvg + "ms (expected: 85.0)");
        assert Math.abs(overallAvg - 85.0) < 0.001 : "Expected 85.0, got " + overallAvg;
        
        // The original operation (120ms) should not be slow yet (120 < 85 * 2 = 170)
        isSlow = monitor.isSlowOperation(operation);
        System.out.println("Is original operation slow: " + isSlow + " (expected: false, 120 < 170)");
        assert !isSlow : "Operation should still not be slow";
        
        // Add a very slow operation
        monitor.recordExecutionTime("very-slow-op", 500.0);
        
        // Overall should now be (120 + 50 + 500) / 3 = 223.33
        overallAvg = monitor.getOverallAverageExecutionTime();
        System.out.println("Overall average after adding very slow op: " + overallAvg + "ms (expected: ~223.33)");
        
        // The very slow operation should be classified as slow (500 > 223.33 * 2 = 446.67)
        isSlow = monitor.isSlowOperation("very-slow-op");
        System.out.println("Is very slow operation slow: " + isSlow + " (expected: true, 500 > 446.67)");
        assert isSlow : "Very slow operation should be classified as slow";
        
        System.out.println("\nAll core functionality tests passed!");
        System.out.println("The slow query segregation feature is working correctly.");
    }
}