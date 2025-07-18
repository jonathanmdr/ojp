# Connection Pool Configuration

OJP now supports configurable connection pool settings via an `ojp.properties` file. This allows you to customize HikariCP connection pool behavior without code changes.

## How to Configure

1. Create an `ojp.properties` file in your application's classpath (either in the root or in the `resources` folder)
2. Add any of the supported properties (all are optional)
3. The driver will automatically load and send these properties to the server when establishing a connection

## Supported Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `maximumPoolSize` | int | 10 | Maximum number of connections in the pool |
| `minimumIdle` | int | 10 | Minimum number of idle connections maintained |
| `idleTimeout` | long | 600000 | Maximum time (ms) a connection can sit idle (10 minutes) |
| `maxLifetime` | long | 1800000 | Maximum lifetime (ms) of a connection (30 minutes) |
| `connectionTimeout` | long | 30000 | Maximum time (ms) to wait for a connection (30 seconds) |
| `autoCommit` | boolean | true | Default auto-commit behavior of connections |
| `poolName` | string | "HikariPool-1" | User-defined name for the connection pool |
| `validationTimeout` | long | 5000 | Maximum time (ms) to test connection aliveness (5 seconds) |
| `leakDetectionThreshold` | long | 0 | Time (ms) before logging potential connection leak (0 = disabled) |
| `isolateInternalQueries` | boolean | false | Whether to isolate internal pool queries |
| `allowPoolSuspension` | boolean | false | Whether to allow pool suspension |

## Example Configuration

```properties
# Example ojp.properties file
maximumPoolSize=20
minimumIdle=5
idleTimeout=300000
maxLifetime=900000
connectionTimeout=15000
autoCommit=false
poolName=MyAppPool
validationTimeout=3000
leakDetectionThreshold=60000
```

## Fallback Behavior

- If no `ojp.properties` file is found, all default values are used
- If a property is missing from the file, its default value is used
- If a property has an invalid value, the default is used and a warning is logged
- All validation and configuration logic is handled on the server side

## File Location Priority

The driver searches for `ojp.properties` in the following order:
1. Root of classpath (`ojp.properties`)
2. Resources folder (`resources/ojp.properties`)

If found in multiple locations, the first one found takes precedence.