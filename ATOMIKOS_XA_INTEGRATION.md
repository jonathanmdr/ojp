# Atomikos XA Integration Guide

## Overview

This document describes the Atomikos XA transaction integration in OJP server, which provides distributed transaction support for XA-capable databases.

## Architecture

### Key Components

1. **AtomikosLifecycle** - Manages the Atomikos UserTransactionService lifecycle
2. **AtomikosDataSourceFactory** - Creates and configures AtomikosDataSourceBean instances
3. **StatementServiceImpl** - Modified to support both Hikari (non-XA) and Atomikos (XA) datasources
4. **GrpcServer** - Integrated with Atomikos lifecycle for startup/shutdown

### Connection Management

The implementation uses a dual-datasource approach:
- **datasourceMap**: Stores HikariDataSource instances for regular connections
- **datasourceXaMap**: Stores AtomikosDataSourceBean instances for XA connections

### Lazy Connection Allocation

Both XA and non-XA connections use lazy allocation:
- Connections are acquired only when executing statements or explicitly requesting a Connection
- Implemented in `StatementServiceImpl.sessionConnection()` method
- Sessions without an active connection receive a SessionInfo with connection metadata only

## Configuration

### Server Configuration Properties

Configure Atomikos transaction logging via system properties or environment variables:

```properties
# Enable/disable transaction logging (default: false)
ojp.jdbc.atomikos.logging.enabled=false

# Transaction log directory (default: ./atomikos-logs)
ojp.jdbc.atomikos.logging.dir=/var/log/atomikos
```

Environment variables (uppercase with underscores):
```bash
export OJP_JDBC_ATOMIKOS_LOGGING_ENABLED=true
export OJP_JDBC_ATOMIKOS_LOGGING_DIR=/var/log/atomikos
```

### Client Configuration Properties

Connection pool properties are mapped from HikariCP configuration keys:

| Client Property | Atomikos Property | Conversion |
|----------------|------------------|------------|
| ojp.connection.pool.maximumPoolSize | setMaxPoolSize | Direct mapping |
| ojp.connection.pool.minimumIdle | setMinPoolSize | Direct mapping |
| ojp.connection.pool.connectionTimeout | setBorrowConnectionTimeout | ms → seconds (min 1) |
| ojp.connection.pool.idleTimeout | setMaxIdleTime | ms → seconds (min 1) |
| ojp.connection.pool.maxLifetime | setReapTimeout | ms → seconds (min 1) |

### Time Conversion

All timeout configurations are kept in milliseconds in configuration files and automatically converted to seconds for Atomikos:

```java
seconds = Math.max(1, Math.round(milliseconds / 1000.0))
```

Minimum value is 1 second, even for values less than 1000ms.

## Usage Examples

### Client-Side XA Connection

```java
import com.openjproxy.grpc.ConnectionDetails;
import org.openjproxy.grpc.SerializationHandler;
import java.util.Properties;

// Configure connection pool properties
Properties props = new Properties();
props.setProperty("ojp.connection.pool.maximumPoolSize", "20");
props.setProperty("ojp.connection.pool.minimumIdle", "5");
props.setProperty("ojp.connection.pool.connectionTimeout", "10000");
props.setProperty("ojp.connection.pool.idleTimeout", "600000");
props.setProperty("ojp.connection.pool.maxLifetime", "1800000");

// Create XA connection details
ConnectionDetails details = ConnectionDetails.newBuilder()
    .setUrl("jdbc:postgresql://localhost:5432/mydb")
    .setUser("myuser")
    .setPassword("mypassword")
    .setClientUUID(UUID.randomUUID().toString())
    .setIsXA(true)  // Enable XA transactions
    .setProperties(ByteString.copyFrom(SerializationHandler.serialize(props)))
    .build();

// Connect using the XA-enabled connection
SessionInfo session = statementService.connect(details);
```

### Server Configuration

Start the server with Atomikos logging enabled:

```bash
java -Dojp.jdbc.atomikos.logging.enabled=true \
     -Dojp.jdbc.atomikos.logging.dir=/var/log/atomikos \
     -jar ojp-server.jar
```

Or with Docker:

```bash
docker run -p 1059:1059 \
  -e OJP_JDBC_ATOMIKOS_LOGGING_ENABLED=true \
  -e OJP_JDBC_ATOMIKOS_LOGGING_DIR=/var/log/atomikos \
  -v /var/log/atomikos:/var/log/atomikos \
  rrobetti/ojp:latest
```

## Supported Databases

The current implementation supports XA transactions for:
- PostgreSQL (via org.postgresql.xa.PGXADataSource)
- MySQL (via com.mysql.cj.jdbc.MysqlXADataSource)
- H2 (via org.h2.jdbcx.JdbcDataSource) - primarily for testing

Additional database support can be added by extending the `createXADataSource()` method in `StatementServiceImpl`.

## Implementation Details

### Atomikos Lifecycle

1. **Startup**: AtomikosLifecycle.start() is called when GrpcServer starts
   - Initializes UserTransactionServiceImp with configuration
   - Sets up transaction logging based on configuration
   - If logging is disabled, uses temp directory with minimal I/O

2. **Shutdown**: AtomikosLifecycle.stop() is called when GrpcServer shuts down
   - Gracefully shuts down UserTransactionService
   - Cleans up resources

### Connection Acquisition Flow

```
Client Request (isXA=true)
    ↓
StatementServiceImpl.connect()
    ↓
Creates XADataSource for database
    ↓
AtomikosDataSourceFactory.createAtomikosDataSource()
    ↓
Wraps XADataSource in AtomikosDataSourceBean
    ↓
Stores in datasourceXaMap
    ↓
Returns SessionInfo (no connection yet - lazy allocation)
    ↓
Client executes statement
    ↓
StatementServiceImpl.sessionConnection()
    ↓
Detects XA session, acquires connection from Atomikos pool
    ↓
Creates session with connection
```

### Transaction Logging

When `ojp.jdbc.atomikos.logging.enabled=true`:
- Transaction logs are written to the specified directory
- Logs enable recovery after crashes
- Directory is automatically created if it doesn't exist

When `ojp.jdbc.atomikos.logging.enabled=false` (default):
- Atomikos uses temp directory with minimal configuration
- No persistent transaction logs
- Checkpoint interval set to very high value to minimize I/O
- Suitable for development and testing

## Testing

### Unit Tests

The `AtomikosIntegrationTest` class provides comprehensive test coverage:

```bash
mvn test -Dtest=AtomikosIntegrationTest -pl ojp-server -am
```

Tests include:
- Atomikos lifecycle (start/stop)
- DataSource creation with configuration
- Connection acquisition
- Milliseconds to seconds conversion
- Default values
- Configuration caching

### Manual Testing

1. Start server with XA support:
```bash
mvn clean install -DskipTests
cd ojp-server
mvn exec:java -Dexec.mainClass="org.openjproxy.grpc.server.GrpcServer" \
  -Dojp.jdbc.atomikos.logging.enabled=true
```

2. Connect with XA-enabled client and verify Atomikos datasource creation in logs

## Performance Considerations

### Connection Pooling

- Atomikos pools are sized independently per datasource
- Pool size configured via standard OJP connection pool properties
- Each XA datasource has its own unique resource name

### Transaction Logging

- When logging is disabled (default), minimal disk I/O
- Enable logging only in production environments requiring crash recovery
- Log directory should be on a reliable filesystem

### Lazy Allocation

- Connections not acquired until first use
- Reduces connection overhead for short-lived sessions
- Pool connections recycled efficiently

## Troubleshooting

### Common Issues

1. **javax.transaction.TransactionManager not found**
   - Ensure javax.transaction-api dependency is present
   - Version 1.3 required

2. **Atomikos fails to start**
   - Check logging directory permissions
   - Verify JTA API is on classpath
   - Review Atomikos configuration in logs

3. **Connection timeout**
   - Increase connectionTimeout property
   - Check database connectivity
   - Verify pool size is adequate

### Debug Logging

Enable Atomikos debug logging:
```bash
-Dcom.atomikos.icatch.console_log_level=DEBUG
```

## Migration from Previous Version

If upgrading from a version without XA support:

1. Existing non-XA connections continue to work unchanged
2. No configuration changes required for existing deployments
3. XA support is opt-in via `isXA=true` flag
4. Server gracefully handles both XA and non-XA connections simultaneously

## Dependencies

Required Maven dependencies (automatically included):

```xml
<!-- Atomikos -->
<dependency>
    <groupId>com.atomikos</groupId>
    <artifactId>transactions-jta</artifactId>
    <version>5.0.8</version>
</dependency>
<dependency>
    <groupId>com.atomikos</groupId>
    <artifactId>transactions-jdbc</artifactId>
    <version>5.0.8</version>
</dependency>
<dependency>
    <groupId>javax.transaction</groupId>
    <artifactId>javax.transaction-api</artifactId>
    <version>1.3</version>
</dependency>
```

## References

- [Atomikos Documentation](https://www.atomikos.com/Documentation/)
- [JTA Specification](https://jcp.org/en/jsr/detail?id=907)
- [XA Transactions](https://en.wikipedia.org/wiki/X/Open_XA)
