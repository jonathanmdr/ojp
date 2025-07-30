# IBM DB2 Integration for OJP

This document describes the IBM DB2 integration support added to the Open JDBC Proxy (OJP) project.

## Overview

IBM DB2 integration has been added to OJP following the same patterns used for Oracle and SQL Server integration. This includes:

- DB2 JDBC driver support in the server
- Comprehensive DB2-specific integration tests
- CI/CD pipeline support (optional)
- DB2-specific SQL syntax and data type handling

## Configuration

### 1. Dependencies

The IBM DB2 JDBC driver (jcc) version 11.5.9.0 has been added to `ojp-server/pom.xml`.

### 2. Driver Registration

The DB2 driver (`com.ibm.db2.jcc.DB2Driver`) is automatically registered when the OJP server starts.

### 3. Connection String Format

DB2 connections through OJP use the following format:
```
jdbc:ojp[server:port]_db2://db2host:db2port/database
```

Example:
```
jdbc:ojp[localhost:1059]_db2://localhost:50000/defaultdb
```

## Test Suite

### Enabling DB2 Tests

DB2 tests are **disabled by default**. To enable them, set the system property:
```bash
-DenableDb2Tests=true
```

### Test Classes

The following DB2-specific test classes have been implemented:

1. **Db2ConnectionExtensiveTests** - Basic connection and DB2-specific functionality
2. **Db2BlobIntegrationTest** - BLOB data type testing and performance
3. **Db2PreparedStatementExtensiveTests** - PreparedStatement operations and batch processing
4. **Db2ResultSetTest** - ResultSet navigation, data types, and metadata
5. **Db2SavepointTests** - Transaction savepoint functionality
6. **Db2DatabaseMetaDataExtensiveTests** - Database metadata and capabilities

### Special DB2 Test Features

- **Boolean Mapping Tests**: Verifies DB2's handling of boolean values and type mapping
- **NULL vs Empty String Tests**: Tests DB2's treatment of NULL and empty string values
- **DB2-specific Data Types**: Tests INTEGER, VARCHAR, DECIMAL, BOOLEAN, DATE, TIMESTAMP, CLOB, and BLOB types
- **DB2 SQL Syntax**: Uses DB2-compatible SQL syntax (e.g., `DATE('2023-12-25')`, `TIMESTAMP('2023-12-25-10.30.00')`)

### Running Tests

```bash
# Run only DB2 tests (requires OJP server and DB2 database running)
mvn test -pl ojp-jdbc-driver -Dtest=Db2* -DenableDb2Tests=true

# Run all tests with DB2 enabled
mvn test -pl ojp-jdbc-driver -DenableDb2Tests=true

# Run tests with DB2 disabled (default - tests will be skipped)
mvn test -pl ojp-jdbc-driver -DenableDb2Tests=false
```

## CI/CD Integration

### GitHub Actions

DB2 database service configuration has been added to `.github/workflows/main.yml` (commented out by default):

```yaml
#db2:
#  image: ibmcom/db2:11.5.8.0
#  env:
#    LICENSE: accept
#    DB2INSTANCE: db2inst1
#    DB2INST1_PASSWORD: testpassword
#    DBNAME: defaultdb
#    # ... additional configuration
#  ports:
#    - 50000:50000
```

To enable DB2 in CI:
1. Uncomment the DB2 service configuration
2. Add `-DenableDb2Tests=true` to test commands in the workflow

## File Structure

### Added Files

```
ojp-jdbc-driver/src/test/resources/
├── db2_connections.csv                    # DB2 connection parameters

ojp-jdbc-driver/src/test/java/openjdbcproxy/jdbc/
├── Db2ConnectionExtensiveTests.java       # Basic DB2 functionality
├── Db2BlobIntegrationTest.java           # BLOB operations
├── Db2PreparedStatementExtensiveTests.java # PreparedStatement tests
├── Db2ResultSetTest.java                 # ResultSet functionality
├── Db2SavepointTests.java                # Transaction savepoints
└── Db2DatabaseMetaDataExtensiveTests.java # Database metadata
```

### Modified Files

```
ojp-server/pom.xml                        # Added DB2 JDBC driver dependency
ojp-server/src/main/java/org/openjdbcproxy/grpc/server/
├── Constants.java                        # Added DB2_DRIVER_CLASS constant
└── utils/DriverUtils.java               # Added DB2 driver registration

ojp-jdbc-driver/src/test/java/openjdbcproxy/jdbc/
├── BasicCrudIntegrationTest.java         # Added DB2 support
└── testutil/TestDBUtils.java            # Added DB2 SQL syntax support

ojp-jdbc-driver/src/test/resources/
└── h2_postgres_mysql_mariadb_oracle_sqlserver_connections.csv # Added DB2 connection

.github/workflows/main.yml               # Added DB2 service (commented)
```

## Usage Examples

### Local Development

1. Start DB2 database (Docker example):
```bash
docker run -it --name db2 --privileged=true \
  -p 50000:50000 \
  -e LICENSE=accept \
  -e DB2INSTANCE=db2inst1 \
  -e DB2INST1_PASSWORD=testpassword \
  -e DBNAME=defaultdb \
  ibmcom/db2:11.5.8.0
```

2. Start OJP server:
```bash
mvn verify -pl ojp-server -Prun-ojp-server
```

3. Run DB2 tests:
```bash
mvn test -pl ojp-jdbc-driver -Dtest=Db2* -DenableDb2Tests=true
```

### Connection Configuration

Update your connection CSV files with DB2 connection strings:
```csv
org.openjdbcproxy.jdbc.Driver,jdbc:ojp[localhost:1059]_db2://localhost:50000/defaultdb,testuser,testpassword
```

## Notes

- DB2 tests are disabled by default following the same pattern as Oracle and SQL Server tests
- The implementation uses DB2-compatible SQL syntax and data types
- All test classes follow the established patterns from Oracle integration
- The integration supports DB2-specific features like boolean handling and NULL vs empty string treatment
- Performance tests are included for BLOB operations and batch processing

## Troubleshooting

1. **Tests are skipped**: Ensure `-DenableDb2Tests=true` is set
2. **Connection refused**: Verify DB2 database is running and accessible
3. **SQL syntax errors**: Check that DB2-specific SQL syntax is being used correctly
4. **Driver not found**: Ensure the DB2 JDBC driver dependency is properly configured