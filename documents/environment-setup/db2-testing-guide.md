# IBM DB2 Database Testing Guide

This document explains how to set up and run IBM DB2 Database tests with OJP.

## Prerequisites

1. **Docker** - Required to run IBM DB2 locally
2. **IBM DB2 JDBC Driver** - Automatically included in dependencies

## Setup Instructions

### 1. Start IBM DB2 Database

Use the official IBM DB2 image for testing:

```bash
docker run -it --name db2 --privileged=true \
  -p 50000:50000 \
  -e LICENSE=accept \
  -e DB2INSTANCE=db2inst1 \
  -e DB2INST1_PASSWORD=testpassword \
  -e DBNAME=defaultdb \
  ibmcom/db2:11.5.8.0
```

Wait for the database to fully start (may take several minutes). You can check the logs:

```bash
docker logs db2
```

### 2. IBM DB2 JDBC Driver

The IBM DB2 JDBC driver is automatically included in the ojp-server dependencies:

```xml
<dependency>
    <groupId>com.ibm.db2</groupId>
    <artifactId>jcc</artifactId>
    <version>11.5.9.0</version>
</dependency>
```

### 3. Start OJP Server

In a separate terminal:
```bash
cd ojp
mvn verify -pl ojp-server -Prun-ojp-server
```

### 4. Run DB2 Tests

To run only DB2 tests:

```bash
cd ojp-jdbc-driver
mvn test -DenableDb2Tests -DdisablePostgresTests -DdisableMySQLTests -DdisableMariaDBTests
```

To run DB2 tests alongside other databases:

```bash
cd ojp-jdbc-driver
mvn test -DenableDb2Tests -DenableOracleTests
```

To run specific DB2 test classes:

```bash
cd ojp-jdbc-driver
mvn test -Dtest=Db2* -DenableDb2Tests=true
```

## Test Configuration Files

- `db2_connections.csv` - DB2-only connection configuration
- `h2_postgres_mysql_mariadb_oracle_sqlserver_connections.csv` - Multi-database configuration including DB2

### Database Connection Details

- **URL**: `jdbc:ojp[localhost:1059]_db2://localhost:50000/defaultdb`
- **User**: `db2inst1`
- **Password**: `testpassword`
- **Database**: `defaultdb`

## Connection String Format

The DB2 connection string for OJP follows this format:

```
jdbc:ojp[localhost:1059]_db2://db2host:50000/database
```

Where:
- `localhost:1059` - OJP server address and port
- `db2://localhost:50000` - DB2 instance
- `database` - Target database name

## Skipping DB2 Tests

DB2 tests are skipped by default, use:
```bash
mvn test
```

Also can explicitly disable DB2 tests as in:

```bash
mvn test -DenableDb2Tests=false
```

## DB2-Specific Test Features

The DB2 test suite includes:

- **Connection & Basic Operations**: DB2-specific connection handling, data types, and CRUD operations
- **BLOB Integration**: Binary large object operations and performance testing
- **PreparedStatement Operations**: Parameter binding, batch processing, and NULL handling
- **ResultSet Functionality**: Navigation, scrolling, data type retrieval, and metadata
- **Transaction Management**: Savepoint operations, rollback, and transaction isolation
- **Database Metadata**: Schema information, table/column metadata, and capability detection
- **Boolean Type Handling**: DB2's boolean data type mapping and edge cases
- **NULL vs Empty String**: Validation of DB2's distinct treatment of NULL and empty string values

To build a Docker image of ojp-server follow the above steps and then follow the [Build ojp-server docker image](/ojp-server/README.md) - OpenTelemetry integration and monitoring setup.