# OJP JDBC Integration Testing Guide

## Overview

This document describes how to run ojp-jdbc integration tests in both XA (distributed transaction) mode and pure JDBC mode using the provided test automation script.

## Prerequisites

### Required

1. **Maven** - Build tool (must be in PATH)
2. **Java 11+** - Required by the project
3. **OJP Server** - Must be running on `localhost:1059`

### Starting the OJP Server

Choose one of the following methods:

#### Option 1: Docker (Recommended)
```bash
docker run --rm -d --network host rrobetti/ojp:latest
```

#### Option 2: From Source
```bash
# In the repository root
cd ojp-server
mvn exec:java -Dexec.mainClass="org.openjproxy.grpc.server.GrpcServer"
```

### Optional (for full test coverage)

- **PostgreSQL** - For PostgreSQL-specific tests
- **MySQL/MariaDB** - For MySQL-specific tests  
- **Oracle** - For Oracle-specific tests
- **SQL Server** - For SQL Server-specific tests
- **DB2** - For DB2-specific tests
- **CockroachDB** - For CockroachDB-specific tests

**Note**: Most tests use H2 (embedded in-memory database) and will run without external databases. Tests requiring external databases will be skipped if the database is not available.

## Running the Tests

### Quick Start

```bash
./run-ojp-jdbc-integration-tests.sh
```

The script will:
1. Detect the Maven build system
2. Check if the OJP server is running
3. Run XA mode tests (tests using `OjpXADataSource`)
4. Run JDBC mode tests (all other integration tests)
5. Generate separate test reports for each mode
6. Create a summary JSON file with results

### What Tests Are Run?

#### XA Mode Tests
Tests that explicitly use XA (distributed transaction) features:
- `PostgresXAIntegrationTest` - XA transaction tests with PostgreSQL
- `OjpXADataSourceTest` - XA DataSource functionality tests
- Any test with "XA" in the class name

#### JDBC Mode Tests  
All other integration tests that use standard JDBC connections:
- Connection tests
- Statement and PreparedStatement tests
- ResultSet tests
- DatabaseMetaData tests
- LOB (Binary/Character Large Object) tests
- Transaction and Savepoint tests
- Multi-database tests

## Understanding Test Results

### Report Locations

After running the script, you'll find:

```
test-reports/ojp-jdbc/
├── xa/
│   ├── TEST-*.xml          # JUnit XML reports for XA tests
│   ├── *.txt               # Text output for each test class
│   ├── failures.json       # Details of any failed XA tests
│   └── test-output.log     # Complete Maven output
├── jdbc/
│   ├── TEST-*.xml          # JUnit XML reports for JDBC tests
│   ├── *.txt               # Text output for each test class
│   ├── failures.json       # Details of any failed JDBC tests
│   └── test-output.log     # Complete Maven output
└── summary.json            # Aggregated results summary
```

### Summary Format

The `summary.json` file contains:

```json
{
  "timestamp": "2025-10-25T14:20:18Z",
  "module": "ojp-jdbc-driver",
  "modes": {
    "xa": {
      "total": 11,
      "passed": 6,
      "failed": 0,
      "errors": 5,
      "skipped": 0,
      "status": "SUCCESS",
      "report_path": "test-reports/ojp-jdbc/xa",
      "failures_path": "test-reports/ojp-jdbc/xa/failures.json"
    },
    "jdbc": {
      "total": 632,
      "passed": 8,
      "failed": 0,
      "errors": 400,
      "skipped": 224,
      "status": "SUCCESS",
      "report_path": "test-reports/ojp-jdbc/jdbc",
      "failures_path": "test-reports/ojp-jdbc/jdbc/failures.json"
    }
  }
}
```

### Console Output

The script provides colored console output showing:
- ✓ Successful checks (green)
- ⚠ Warnings (yellow)  
- ✗ Errors (red)
- Test execution progress
- Final summary with pass/fail counts

## Common Issues

### "UNAVAILABLE: io exception"

**Problem**: Tests fail with `io.grpc.StatusRuntimeException: UNAVAILABLE: io exception`

**Solution**: The OJP server is not running. Start it using one of the methods described above.

### High Error Count

**Problem**: Many tests show errors even though the server is running.

**Reason**: Tests require specific databases (PostgreSQL, MySQL, Oracle, etc.) that may not be configured.

**Expected Behavior**: 
- Tests using H2 (embedded) should pass
- Tests for external databases will error if those databases aren't available
- Errors for unavailable databases are expected in local development

### Script Won't Run

**Problem**: `bash: ./run-ojp-jdbc-integration-tests.sh: Permission denied`

**Solution**: Make the script executable:
```bash
chmod +x run-ojp-jdbc-integration-tests.sh
```

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    services:
      ojp-server:
        image: rrobetti/ojp:latest
        ports:
          - 1059:1059
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 11
        uses: actions/setup-java@v3
        with:
          java-version: '11'
          distribution: 'temurin'
      
      - name: Run integration tests
        run: ./run-ojp-jdbc-integration-tests.sh
      
      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: test-reports/
```

## Advanced Usage

### Running Only XA Tests

```bash
mvn -pl ojp-jdbc-driver -am test \
  -Dtest='*XA*Test' \
  -DdisablePostgresXATests=false
```

### Running Only JDBC Tests

```bash
mvn -pl ojp-jdbc-driver -am test \
  -Dtest='!*XA*Test'
```

### Running Specific Test Classes

```bash
mvn -pl ojp-jdbc-driver -am test \
  -Dtest='PostgresXAIntegrationTest'
```

## Test Architecture

### How Tests Determine Mode

Tests don't globally switch between XA and JDBC mode. Instead:

1. **XA Tests** explicitly use `OjpXADataSource`:
   ```java
   OjpXADataSource xaDataSource = new OjpXADataSource();
   xaDataSource.setUrl(url);
   XAConnection xaConnection = xaDataSource.getXAConnection(user, password);
   ```

2. **JDBC Tests** use regular `DriverManager` or `DataSource`:
   ```java
   Connection conn = DriverManager.getConnection(ojpUrl, user, password);
   ```

The `isXA` flag in the ConnectionDetails protobuf is set automatically:
- `true` when using XADataSource
- `false` (default) for regular connections

### Server-Side Handling

The OJP server handles both XA and non-XA connections simultaneously:
- XA connections use Atomikos transaction manager
- Non-XA connections use HikariCP connection pool
- Connection type is determined per-session based on the `isXA` flag

## Troubleshooting

### Viewing Detailed Logs

Check the test output logs:
```bash
# XA tests
less test-reports/ojp-jdbc/xa/test-output.log

# JDBC tests  
less test-reports/ojp-jdbc/jdbc/test-output.log
```

### Viewing Specific Test Failures

```bash
# List failed tests
cat test-reports/ojp-jdbc/xa/failures.json
cat test-reports/ojp-jdbc/jdbc/failures.json
```

### Re-running with Debug Output

```bash
mvn -pl ojp-jdbc-driver -am test \
  -Dtest='*XA*Test' \
  -X  # Enable debug output
```

## Contributing

When adding new integration tests:

1. **For XA tests**: 
   - Name the class with "XA" in it (e.g., `MyFeatureXATest`)
   - Use `OjpXADataSource`
   - Add to XA test pattern in script if needed

2. **For JDBC tests**:
   - Use standard JDBC APIs
   - Will be automatically included in JDBC test run

## References

- [ATOMIKOS_XA_INTEGRATION.md](ATOMIKOS_XA_INTEGRATION.md) - Detailed XA integration documentation
- [OJP Documentation](README.md) - Main project documentation
- [Maven Surefire Plugin](https://maven.apache.org/surefire/maven-surefire-plugin/) - Test execution details
