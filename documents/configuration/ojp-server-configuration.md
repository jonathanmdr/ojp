# OJP Server Configuration

The OJP Server supports comprehensive configuration through both JVM system properties and environment variables. JVM system properties take precedence over environment variables when both are specified.

## Configuration Options

| Property                             | Environment Variable                 | Type    | Default   | Description                                            |
|--------------------------------------|--------------------------------------|---------|-----------|--------------------------------------------------------|
| `ojp.server.port`                    | `OJP_SERVER_PORT`                    | int     | 1059      | gRPC server port                                       |
| `ojp.prometheus.port`                | `OJP_PROMETHEUS_PORT`                | int     | 9090      | Prometheus metrics HTTP server port                    |
| `ojp.opentelemetry.enabled`          | `OJP_OPENTELEMETRY_ENABLED`          | boolean | true      | Enable/disable OpenTelemetry instrumentation           |
| `ojp.opentelemetry.endpoint`         | `OJP_OPENTELEMETRY_ENDPOINT`         | string  | ""        | OpenTelemetry exporter endpoint (empty = default)      |
| `ojp.server.threadPoolSize`          | `OJP_SERVER_THREADPOOLSIZE`          | int     | 200       | gRPC server thread pool size                           |
| `ojp.server.maxRequestSize`          | `OJP_SERVER_MAXREQUESTSIZE`          | int     | 4194304   | Maximum request size in bytes (4MB)                    |
| `ojp.server.logLevel`                | `OJP_SERVER_LOGLEVEL`                | string  | INFO      | Log verbosity level                                    |
| `ojp.server.accessLogging`           | `OJP_SERVER_ACCESSLOGGING`           | boolean | false     | Enable/disable access logging                          |
| `ojp.server.allowedIps`              | `OJP_SERVER_ALLOWEDIPS`              | string  | 0.0.0.0/0 | IP whitelist for gRPC server (comma-separated)         |
| `ojp.server.connectionIdleTimeout`   | `OJP_SERVER_CONNECTIONIDLETIMEOUT`   | long    | 30000     | Connection idle timeout in milliseconds                |
| `ojp.server.circuitBreakerTimeout`   | `OJP_SERVER_CIRCUITBREAKERTIMEOUT`   | long    | 60000     | Circuit breaker timeout in milliseconds                |
| `ojp.server.circuitBreakerThreshold` | `OJP_SERVER_CIRCUITBREAKERTHRESHOLD` | int     | 3         | Circuit breaker failure threshold      |
| `ojp.prometheus.allowedIps`          | `OJP_PROMETHEUS_ALLOWEDIPS`          | string  | 0.0.0.0/0 | IP whitelist for Prometheus endpoint (comma-separated) |

## Configuration Methods

### 1. JVM System Properties

Set configuration using JVM system properties when starting the server:

```bash
java -Dojp.server.port=8080 \
     -Dojp.prometheus.port=9091 \
     -Dojp.opentelemetry.enabled=false \
     -Dojp.server.threadPoolSize=100 \
     -Dojp.server.circuitBreakerTimeout=120000 \
     -Dojp.server.circuitBreakerThreshold=3 \
     -Dojp.server.allowedIps="192.168.1.0/24,10.0.0.1" \
     -jar ojp-server.jar
```

### 2. Environment Variables

Set configuration using environment variables:

```bash
export OJP_SERVER_PORT=8080
export OJP_PROMETHEUS_PORT=9091
export OJP_OPENTELEMETRY_ENABLED=false
export OJP_SERVER_THREADPOOLSIZE=100
export OJP_SERVER_CIRCUITBREAKERTIMEOUT=120000
export OJP_SERVER_CIRCUITBREAKERTHRESHOLD=3
export OJP_SERVER_ALLOWEDIPS="192.168.1.0/24,10.0.0.1"
java -jar ojp-server.jar
```

### 3. Docker Environment Variables

```bash
docker run -e OJP_SERVER_PORT=8080 \
           -e OJP_PROMETHEUS_PORT=9091 \
           -e OJP_OPENTELEMETRY_ENABLED=false \
           -e OJP_SERVER_CIRCUITBREAKERTIMEOUT=120000 \
           -e OJP_SERVER_ALLOWEDIPS="192.168.1.0/24,10.0.0.1" \
           -p 8080:8080 \
           -p 9091:9091 \
           rrobetti/ojp:latest
```

## IP Whitelist Configuration

The server supports IP-based access control for both the gRPC server and Prometheus endpoints. IP whitelist rules support:

### Supported Formats

- **Individual IP addresses**: `192.168.1.1`
- **CIDR ranges**: `192.168.1.0/24`, `10.0.0.0/8`
- **Wildcard (allow all)**: `0.0.0.0/0` or `*`
- **Multiple rules**: `192.168.1.1,10.0.0.0/8,172.16.0.1`

### Examples

```bash
# Allow only specific IPs
-Dojp.server.allowedIps="192.168.1.100,192.168.1.101"

# Allow a subnet range
-Dojp.server.allowedIps="192.168.1.0/24"

# Allow multiple subnets and specific IPs
-Dojp.server.allowedIps="192.168.1.0/24,10.0.0.0/8,127.0.0.1"

# Allow all (default)
-Dojp.server.allowedIps="0.0.0.0/0"
```

### Separate Prometheus Whitelist

You can configure different IP restrictions for the Prometheus metrics endpoint:

```bash
# Allow gRPC from internal network, Prometheus from monitoring subnet only
-Dojp.server.allowedIps="10.0.0.0/8" \
-Dojp.prometheus.allowedIps="192.168.100.0/24"
```

## Configuration Validation

- **Invalid values**: Fall back to defaults with warning logs
- **Invalid IP rules**: Server startup fails with error
- **Port conflicts**: Standard socket binding errors apply
- **Type mismatches**: Automatic fallback to defaults

## OpenTelemetry Configuration

### Enable/Disable Telemetry

```bash
# Disable OpenTelemetry completely
-Dojp.opentelemetry.enabled=false

# Enable with custom Prometheus port
-Dojp.opentelemetry.enabled=true -Dojp.prometheus.port=9092
```

### Custom Endpoint (Future)

The `ojp.opentelemetry.endpoint` property is reserved for future use with OTLP exporters:

```bash
# Future feature - custom OTLP endpoint
-Dojp.opentelemetry.endpoint="http://jaeger:14250"
```

## Logging Configuration

### Log Levels

Supported log levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`

```bash
# Enable debug logging
-Dojp.server.logLevel=DEBUG

# Enable access logging
-Dojp.server.accessLogging=true
```

## Performance Tuning

### Circuit Breaker Configuration

The circuit breaker protects against cascading failures by tracking SQL statement failures and temporarily blocking requests when failure thresholds are exceeded:

```bash
# Default circuit breaker timeout (60 seconds)
-Dojp.server.circuitBreakerTimeout=60000

# Extended timeout for environments with occasional slow queries
-Dojp.server.circuitBreakerTimeout=120000

# Short timeout for fast-fail scenarios
-Dojp.server.circuitBreakerTimeout=30000
```

### Thread Pool Configuration

```bash
# High-throughput configuration
-Dojp.server.threadPoolSize=500 \
-Dojp.server.maxRequestSize=16777216 \
-Dojp.server.connectionIdleTimeout=60000 \
-Dojp.server.circuitBreakerTimeout=90000
```

### Memory and Request Limits

```bash
# Larger request size for big result sets
-Dojp.server.maxRequestSize=16777216  # 16MB

# Longer connection timeouts for slow queries
-Dojp.server.connectionIdleTimeout=120000  # 2 minutes

# Longer circuit breaker timeout for complex queries
-Dojp.server.circuitBreakerTimeout=180000  # 3 minutes
```


### Slow Query Segregation Configuration

```bash
# Enable or disable the slow query segregation feature
ojp.server.slowQuerySegregation.enabled=true
# Percentage of execution slots allocated to slow operations (0-100)
# Default: 20% for slow, 80% for fast operations
ojp.server.slowQuerySegregation.slowSlotPercentage=20
# Idle timeout before slots can be borrowed between pools (milliseconds)
# Default: 10 seconds
ojp.server.slowQuerySegregation.idleTimeout=10000

# Timeout for acquiring slow operation slots (milliseconds)
# Default: 120 seconds
ojp.server.slowQuerySegregation.slowSlotTimeout=120000

# Timeout for acquiring fast operation slots (milliseconds)
# Default: 60 seconds
ojp.server.slowQuerySegregation.fastSlotTimeout=60000
```

## Configuration Examples

### Development Environment

```bash
java -Dojp.server.port=1059 \
     -Dojp.prometheus.port=9090 \
     -Dojp.server.logLevel=DEBUG \
     -Dojp.server.accessLogging=true \
     -Dojp.server.allowedIps="0.0.0.0/0" \
     -jar ojp-server.jar
```

### Production Environment

```bash
java -Dojp.server.port=1059 \
     -Dojp.prometheus.port=9090 \
     -Dojp.server.logLevel=INFO \
     -Dojp.server.accessLogging=false \
     -Dojp.server.threadPoolSize=300 \
     -Dojp.server.circuitBreakerTimeout=60000 \
     -Dojp.server.allowedIps="10.0.0.0/8,172.16.0.0/12" \
     -Dojp.prometheus.allowedIps="192.168.100.0/24" \
     -jar ojp-server.jar
```

### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: ojp-server-config
data:
  OJP_SERVER_PORT: "1059"
  OJP_PROMETHEUS_PORT: "9090"
  OJP_SERVER_THREADPOOLSIZE: "200"
  OJP_SERVER_CIRCUITBREAKERTIMEOUT: "60000"
  OJP_SERVER_ALLOWEDIPS: "10.244.0.0/16"
  OJP_PROMETHEUS_ALLOWEDIPS: "10.244.0.0/16"
  OJP_OPENTELEMETRY_ENABLED: "true"
  OJP_SERVER_LOGLEVEL: "INFO"
```

## Troubleshooting

### Common Issues

1. **Server won't start**: Check IP whitelist configuration and port availability
2. **Can't connect**: Verify client IP is in the allowed list
3. **Metrics unavailable**: Check Prometheus port and IP whitelist
4. **Performance issues**: Adjust thread pool size and connection timeouts

### Debugging Configuration

Enable debug logging to see configuration loading:

```bash
-Dojp.server.logLevel=DEBUG
```

Configuration summary is logged at startup:

```
INFO org.openjdbcproxy.grpc.server.ServerConfiguration - OJP Server Configuration:
INFO org.openjdbcproxy.grpc.server.ServerConfiguration -   Server Port: 1059
INFO org.openjdbcproxy.grpc.server.ServerConfiguration -   Prometheus Port: 9090
INFO org.openjdbcproxy.grpc.server.ServerConfiguration -   OpenTelemetry Enabled: true
...
```