# Connection Pool Configuration

OJP now supports configurable connection pool settings via an `ojp.properties` file. This allows you to customize HikariCP connection pool behavior without code changes.

## How to Configure

1. Create an `ojp.properties` file in your application's classpath (either in the root or in the `resources` folder)
2. Add any of the supported properties (all are optional)
3. The driver will automatically load and send these properties to the server when establishing a connection

## Supported Properties

| Property                              | Type | Default | Description |
|---------------------------------------|------|---------|-------------|
| `ojp.connection.pool.maximumPoolSize` | int | 10 | Maximum number of connections in the pool |
| `ojp.connection.pool.minimumIdle`                         | int | 10 | Minimum number of idle connections maintained |
| `ojp.connection.pool.idleTimeout`                         | long | 600000 | Maximum time (ms) a connection can sit idle (10 minutes) |
| `ojp.connection.pool.maxLifetime`                         | long | 1800000 | Maximum lifetime (ms) of a connection (30 minutes) |
| `ojp.connection.pool.connectionTimeout`                   | long | 30000 | Maximum time (ms) to wait for a connection (30 seconds) |

## Example Configuration

```properties
# Example ojp.properties file
ojp.connection.pool.maximumPoolSize=20
ojp.connection.pool.minimumIdle=5
ojp.connection.pool.idleTimeout=300000
ojp.connection.pool.maxLifetime=900000
ojp.connection.pool.connectionTimeout=15000
```

## Fallback Behavior

- If no `ojp.properties` file is found, all default values are used
- If a property is missing from the file, its default value is used
- If a property has an invalid value, the default is used and a warning is logged
- All validation and configuration logic is handled on the server side

## File Location Priority

The driver searches for `ojp.properties` in the `resources/ojp.properties` folder.
