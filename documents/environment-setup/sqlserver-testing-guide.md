# SQL Server Database Testing Guide

This document explains how to set up and run SQL Server tests with OJP.

## Prerequisites

1. **Docker** - Required to run SQL Server locally
2. **Microsoft SQL Server JDBC Driver** - Automatically included in dependencies

## Setup Instructions

### 1. Start SQL Server Database

Use the official Microsoft SQL Server image for testing:

```bash
docker run --name ojp-sqlserver -e ACCEPT_EULA=Y -e SA_PASSWORD=TestPassword123! -d -p 1433:1433 mcr.microsoft.com/mssql/server:2022-latest
```

Wait for the database to fully start (may take a few minutes). You can check the logs:

```bash
docker logs ojp-sqlserver
```

### 2. Create Test Database (Optional)

Connect to the SQL Server instance and create a test database:

```bash
docker exec -it ojp-sqlserver /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P TestPassword123!
```

Then run:
```sql
CREATE DATABASE defaultdb;
GO
CREATE LOGIN testuser WITH PASSWORD = 'testpassword';
GO
USE defaultdb;
GO
CREATE USER testuser FOR LOGIN testuser;
GO
ALTER ROLE db_datareader ADD MEMBER testuser;
GO
ALTER ROLE db_datawriter ADD MEMBER testuser;
GO
ALTER ROLE db_ddladmin ADD MEMBER testuser;
GO
```

### 3. SQL Server JDBC Driver

The Microsoft SQL Server JDBC driver is automatically included in the ojp-server dependencies:

```xml
<dependency>
    <groupId>com.microsoft.sqlserver</groupId>
    <artifactId>mssql-jdbc</artifactId>
    <version>12.8.1.jre11</version>
</dependency>
```

### 4. Start OJP Server

In a separate terminal:
```bash
cd ojp
mvn verify -pl ojp-server -Prun-ojp-server
```

### 5. Run SQL Server Tests

To run only SQL Server tests:

```bash
cd ojp-jdbc-driver
mvn test -DenableSqlServerTests -DdisablePostgresTests -DdisableMySQLTests -DdisableMariaDBTests
```

To run SQL Server tests alongside other databases:

```bash
cd ojp-jdbc-driver
mvn test -DenableSqlServerTests -DenableOracleTests
```

## Test Configuration Files

- `sqlserver_connections.csv` - SQL Server-only connection configuration
- `h2_postgres_mysql_mariadb_oracle_sqlserver_connections.csv` - Multi-database configuration including SQL Server

## SQL Server Test Classes

The following SQL Server-specific test classes are available:

### Core Functionality Tests
- `SQLServerConnectionExtensiveTests` - Connection management, transactions, Unicode support
- `SQLServerDatabaseMetaDataExtensiveTests` - Database metadata validation (115+ assertions)
- `SQLServerStatementExtensiveTests` - SQL Server-specific syntax (TOP, OFFSET/FETCH, OUTPUT)
- `SQLServerPreparedStatementExtensiveTests` - Parameterized queries and batch operations

### Data Type Tests
- `SQLServerMultipleTypesIntegrationTest` - All SQL Server data types including MONEY, UNIQUEIDENTIFIER, DATETIME2
- `SQLServerBinaryStreamIntegrationTest` - VARBINARY and VARBINARY(MAX) handling
- `SQLServerBlobIntegrationTest` - BLOB-like functionality using VARBINARY(MAX)

### Advanced Functionality Tests
- `SQLServerResultSetTest` - Result set navigation and scrollable cursors
- `SQLServerResultSetMetaDataExtensiveTests` - Column metadata and SQL Server-specific types
- `SQLServerReadMultipleBlocksOfDataIntegrationTest` - Large result sets, pagination, streaming
- `SQLServerSavepointTests` - Transaction savepoints and nested transactions

## SQL Server-Specific Features Tested

### Data Types
- **Standard Types**: INT, BIGINT, SMALLINT, TINYINT, FLOAT, REAL, DECIMAL, NUMERIC
- **String Types**: VARCHAR, NVARCHAR, CHAR, NCHAR, TEXT, NTEXT
- **Unicode Types**: NVARCHAR(MAX), NTEXT with Unicode support (中文, 🚀)
- **Binary Types**: BINARY, VARBINARY, VARBINARY(MAX), IMAGE
- **Date/Time Types**: DATE, TIME, DATETIME, DATETIME2, SMALLDATETIME, DATETIMEOFFSET
- **Special Types**: MONEY, SMALLMONEY, UNIQUEIDENTIFIER, BIT, XML, SQL_VARIANT
- **Spatial Types**: GEOMETRY, GEOGRAPHY
- **Hierarchical Types**: HIERARCHYID

### SQL Server-Specific Syntax
- **IDENTITY Columns**: Auto-incrementing primary keys
- **TOP Clause**: `SELECT TOP 10 * FROM table`
- **OFFSET/FETCH**: `ORDER BY id OFFSET 10 ROWS FETCH NEXT 10 ROWS ONLY`
- **OUTPUT Clause**: `INSERT ... OUTPUT INSERTED.id VALUES ...`
- **IF OBJECT_ID**: `IF OBJECT_ID('table', 'U') IS NOT NULL DROP TABLE table`
- **Quoted Identifiers**: `[column name]` or `"column name"`
- **Unicode Literals**: `N'Unicode text'`

### Advanced Features
- **Stored Procedures**: Creating and calling T-SQL procedures
- **Savepoints**: Named transaction savepoints
- **Batch Operations**: Multiple statement execution
- **Large Data Streaming**: VARBINARY(MAX), NVARCHAR(MAX)
- **Computed Columns**: AS expressions in table definitions
- **Default Constraints**: DEFAULT GETDATE(), DEFAULT NEWID()

## Connection String Format

The SQL Server connection string for OJP follows this format:

```
jdbc:ojp[localhost:1059]_sqlserver://localhost:1433;databaseName=defaultdb;encrypt=false;trustServerCertificate=true
```

Where:
- `localhost:1059` - OJP server address and port
- `sqlserver://localhost:1433` - SQL Server instance
- `databaseName=defaultdb` - Target database
- `encrypt=false;trustServerCertificate=true` - SSL configuration for testing

## Troubleshooting

### Common Issues

1. **Connection Timeout**
   - Ensure SQL Server container is fully started
   - Check if port 1433 is accessible
   - Verify firewall settings

2. **Authentication Failed**
   - Confirm SA password is correct
   - Ensure testuser exists and has proper permissions
   - Check SQL Server authentication mode

3. **Database Not Found**
   - Verify database 'defaultdb' exists
   - Check database name in connection string
   - Ensure user has access to the database

4. **SSL/TLS Errors**
   - Use `encrypt=false;trustServerCertificate=true` for testing
   - For production, configure proper SSL certificates

### Performance Considerations

- SQL Server may require more memory than other databases
- Consider adjusting Docker memory limits for better performance
- Use connection pooling for concurrent tests

## Test Coverage

The SQL Server integration provides comprehensive coverage of:

- ✅ Basic CRUD operations
- ✅ All standard SQL data types
- ✅ SQL Server-specific data types and syntax
- ✅ Transaction management and savepoints
- ✅ Prepared statements and batch operations
- ✅ Binary data and large object handling
- ✅ Result set navigation and metadata
- ✅ Connection pooling and concurrency
- ✅ Error handling and edge cases
- ✅ Unicode and internationalization support

## Environment Variables

You can customize the SQL Server test configuration using environment variables:

```bash
export SQLSERVER_URL="sqlserver://localhost:1433"
export SQLSERVER_DATABASE="defaultdb"
export SQLSERVER_USER="testuser"
export SQLSERVER_PASSWORD="testpassword"
```

These can be reflected in your test configuration files as needed.