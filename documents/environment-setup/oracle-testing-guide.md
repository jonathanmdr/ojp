# Oracle Database Testing Guide

This document explains how to set up and run Oracle Database tests with OJP.

## Prerequisites

1. **Docker** - Required to run Oracle Database locally
2. **Oracle JDBC Driver** - Must be manually downloaded and installed

## Setup Instructions

### 1. Start Oracle Database

Use the community Oracle XE image for testing:

```bash
docker run --name ojp-oracle -e ORACLE_PASSWORD=testpassword -e APP_USER=testuser -e APP_USER_PASSWORD=testpassword -d -p 1521:1521 gvenzl/oracle-xe:21-slim
```

Wait for the database to fully start (may take a few minutes).

### 2. Download and Install Oracle JDBC Driver

Oracle JDBC drivers are not available on Maven Central due to licensing restrictions.

1. Download `ojdbc8.jar` or newer from: https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html

2. Install to your local Maven repository:
```bash
mvn install:install-file -Dfile=ojdbc8.jar -DgroupId=com.oracle.database.jdbc -DartifactId=ojdbc8 -Dversion=21.1.0.0 -Dpackaging=jar
```

### 3. Start OJP Server

In a separate terminal:
```bash
cd ojp
mvn verify -pl ojp-server -Prun-ojp-server
```

### 4. Run Oracle Tests

```bash
cd ojp-jdbc-driver
mvn test -DdisablePostgresTests -DdisableMySQLTests -DdisableMariaDBTests
```

To run only Oracle tests:
```bash
mvn test -Dtest="OracleConnectionExtensiveTests"
```

## Test Configuration Files

- `oracle_connections.csv` - Oracle-only connection configuration
- `h2_oracle_connections.csv` - Combined H2 and Oracle configuration for mixed testing

## Oracle-Specific Features Tested

- **Data Types**: NUMBER, VARCHAR2, BINARY_DOUBLE, BINARY_FLOAT, RAW, DATE, TIMESTAMP
- **Auto-increment**: IDENTITY columns (Oracle 12c+)
- **SQL Syntax**: Oracle-specific CREATE TABLE and data manipulation
- **Connection Handling**: Oracle thin driver compatibility

## Troubleshooting

### Common Issues

1. **"Table or view does not exist"** - Oracle doesn't support `DROP TABLE IF EXISTS`, so the test utils handle this gracefully
2. **"TNS: listener does not currently know of service"** - Database may still be starting up, wait a few minutes
3. **"No suitable driver found"** - Oracle JDBC driver not installed in Maven repository

### Database Connection Details

- **URL**: `jdbc:ojp[localhost:1059]_oracle:thin:@localhost:1521/XEPDB1`
- **User**: `testuser`
- **Password**: `testpassword`
- **Service**: `XEPDB1` (Oracle XE pluggable database)

## Skipping Oracle Tests

To skip Oracle tests, use:
```bash
mvn test -DdisableOracleTests=true
```

This is useful when Oracle is not available or the JDBC driver is not installed.