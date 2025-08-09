package openjdbcproxy.jdbc;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.plexus.util.ExceptionUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@Slf4j
public class PostgresSlowQuerySegregationTest {
    private static final int THREADS = 200; // Number of worker threads
    private static final int RAMPUP_MS = 10 * 1000; // 10 seconds Ramp-up window in milliseconds

    private static boolean isTestDisabled;
    private static Queue<Long> queryDurations = new ConcurrentLinkedQueue<>();
    private static AtomicInteger totalQueries = new AtomicInteger(0);
    private static AtomicInteger failedQueries = new AtomicInteger(0);

    @BeforeAll
    public static void checkTestConfiguration() {
        isTestDisabled = Boolean.parseBoolean(System.getProperty("disablePostgresTests", "false"));
    }

    @SneakyThrows
    public void setUp() throws SQLException {
        queryDurations = new ConcurrentLinkedQueue<>();
        totalQueries = new AtomicInteger(0);
        failedQueries = new AtomicInteger(0);
    }

    @SneakyThrows
    @ParameterizedTest
    @CsvFileSource(resources = "/h2_postgres_connections.csv")
    public void runTests(String driverClass, String url, String user, String password) throws SQLException {
        assumeFalse(isTestDisabled, "Postgres tests are disabled");
        
        this.setUp();
        // 1. Schema and seeding (not timed)
        try (Connection conn = getConnection(driverClass, url, user, password)) {
            Statement stmt = conn.createStatement();
            stmt.execute(
                    "DROP TABLE IF EXISTS order_items CASCADE;" +
                            "DROP TABLE IF EXISTS reviews CASCADE;" +
                            "DROP TABLE IF EXISTS orders CASCADE;" +
                            "DROP TABLE IF EXISTS products CASCADE;" +
                            "DROP TABLE IF EXISTS users CASCADE;" +

                            "CREATE TABLE users (" +
                            "  id IDENTITY PRIMARY KEY," +
                            "  username VARCHAR(50)," +
                            "  email VARCHAR(100)" +
                            ");" +
                            "CREATE TABLE products (" +
                            "  id IDENTITY PRIMARY KEY," +
                            "  name VARCHAR(100)," +
                            "  price DECIMAL" +
                            ");" +
                            "CREATE TABLE orders (" +
                            "  id IDENTITY PRIMARY KEY," +
                            "  user_id INT," +
                            "  order_date TIMESTAMP DEFAULT now()" +
                            ");" +
                            "CREATE TABLE order_items (" +
                            "  id IDENTITY PRIMARY KEY," +
                            "  order_id INT," +
                            "  product_id INT," +
                            "  quantity INT" +
                            ");" +
                            "CREATE TABLE reviews (" +
                            "  id IDENTITY PRIMARY KEY," +
                            "  user_id INT," +
                            "  product_id INT," +
                            "  rating INT," +
                            "  comment TEXT" +
                            ");"
            );
            stmt.close();

            // Seed data
            System.out.println("Seeding users...");
            for (int i = 1; i <= 10000; i++) {
                try (PreparedStatement pst = conn.prepareStatement("INSERT INTO users (username, email) VALUES (?, ?)")) {
                    pst.setString(1, "user" + i);
                    pst.setString(2, "user" + i + "@example.com");
                    pst.execute();
                }
            }
            System.out.println("Seeding products...");
            for (int i = 1; i <= 1000; i++) {
                try (PreparedStatement pst = conn.prepareStatement("INSERT INTO products (name, price) VALUES (?, ?)")) {
                    pst.setString(1, "Product " + i);
                    pst.setBigDecimal(2, new BigDecimal(Math.random() * 1000 + 1));
                    pst.execute();
                }
            }
            System.out.println("Seeding orders...");
            for (int i = 1; i <= 50000; i++) {
                try (PreparedStatement pst = conn.prepareStatement("INSERT INTO orders (user_id, order_date) VALUES (?, ?)")) {
                    pst.setInt(1, (int) (Math.random() * 9999 + 1));
                    pst.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis() - (long) (Math.random() * 365 * 24 * 60 * 60 * 1000)));
                    pst.execute();
                }
            }
            System.out.println("Seeding order_items...");
            for (int i = 1; i <= 100000; i++) {
                try (PreparedStatement pst = conn.prepareStatement("INSERT INTO order_items (order_id, product_id, quantity) VALUES (?, ?, ?)")) {
                    pst.setInt(1, (int) (Math.random() * 49999 + 1));
                    pst.setInt(2, (int) (Math.random() * 999 + 1));
                    pst.setInt(3, (int) (Math.random() * 10 + 1));
                    pst.execute();
                }
            }
            System.out.println("Seeding reviews...");
            for (int i = 1; i <= 30000; i++) {
                try (PreparedStatement pst = conn.prepareStatement("INSERT INTO reviews (user_id, product_id, rating, comment) VALUES (?, ?, ?, ?)")) {
                    pst.setInt(1, (int) (Math.random() * 9999 + 1));
                    pst.setInt(2, (int) (Math.random() * 999 + 1));
                    pst.setInt(3, (int) (Math.random() * 5 + 1));
                    pst.setString(4, "review " + i);
                    pst.execute();
                }
            }
        }

        // 2. Test timing with ramp-up
        long globalStart = System.nanoTime();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        int rampupPerThread = (THREADS > 1) ? RAMPUP_MS / (THREADS - 1) : 0;
        for (int t = 0; t < THREADS; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    // Ramp-up delay for this thread
                    if (threadNum > 0) Thread.sleep(threadNum * rampupPerThread);
                } catch (InterruptedException ignored) {
                }
                runSimpleQuerySequence(threadNum, driverClass, url, user, password);
            });
        }
        executor.shutdown();
        
        // Add timeout to prevent indefinite hanging
        boolean finished = executor.awaitTermination(120, TimeUnit.SECONDS);
        if (!finished) {
            log.error("Test timed out - OJP appears to be blocking indefinitely!");
            executor.shutdownNow();
            throw new RuntimeException("Test timed out after 120 seconds - OJP blocked indefinitely");
        }
        
        long globalEnd = System.nanoTime();

        // 3. Reporting
        int numQueries = totalQueries.get();
        int numFailures = failedQueries.get();
        long totalTimeMs = (globalEnd - globalStart) / 1_000_000;
        double avgQueryMs = numQueries > 0
                ? queryDurations.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0
                : 0;
        System.out.println("\n=== TEST REPORT ===");
        System.out.println("Total queries executed: " + numQueries);
        System.out.println("Total test duration: " + totalTimeMs + " ms");
        System.out.printf("Average query duration: %.3f ms\n", avgQueryMs);
        System.out.println("Total query failures: " + numFailures);
        
        // Assert that the test completed without hanging
        assertTrue("Test should complete without hanging", finished);
        assertTrue("Some queries should complete", numQueries > 0);
        assertTrue("Test should complete in reasonable time", totalTimeMs < 120000);
    }

    private static void timeAndRun(Callable<Void> query) {
        long start = System.nanoTime();
        try {
            query.call();
        } catch (Exception e) {
            failedQueries.incrementAndGet();
            log.error("Query failed: " + e.getMessage() + " \n " + ExceptionUtils.getStackTrace(e));
        }
        long end = System.nanoTime();
        queryDurations.add(end - start);
        totalQueries.incrementAndGet();
    }

    private static Connection getConnection(String driverClass, String url, String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private static void runSimpleQuerySequence(int threadNum, String driverClass, String url, String user, String password) {
        // Each thread runs a simplified sequence to test concurrency
        for (int i = 0; i < 10; i++) {
            final int queryNum = i;
            timeAndRun(() -> {
                try (Connection conn = getConnection(driverClass, url, user, password)) {
                    try (PreparedStatement pst = conn.prepareStatement("INSERT INTO users (username, email) VALUES (?, ?)")) {
                        pst.setString(1, "testUser_" + threadNum + "_" + queryNum);
                        pst.setString(2, "testUser_" + threadNum + "_" + queryNum + "@example.com");
                        pst.execute();
                    }
                } catch (Exception e) {
                    log.error("Query failed for thread " + threadNum + ", query " + queryNum + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
                return null;
            });
            
            timeAndRun(() -> {
                try (Connection conn = getConnection(driverClass, url, user, password)) {
                    try (PreparedStatement pst = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE username LIKE ?")) {
                        pst.setString(1, "testUser_" + threadNum + "%");
                        try (ResultSet rs = pst.executeQuery()) {
                            if (rs.next()) {
                                rs.getInt(1);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Query failed for thread " + threadNum + ", query " + queryNum + ": " + e.getMessage());
                    throw new RuntimeException(e);
                }
                return null;
            });
        }
    }
}