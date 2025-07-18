# Telemetry Documentation

OJP (Open JDBC Proxy) provides comprehensive observability features to monitor and trace database operations through its telemetry system.

## Supported Telemetry Features

### Distributed Tracing with OpenTelemetry
OJP implements distributed tracing using the OpenTelemetry standard, allowing you to:
- Track SQL operations across the proxy and database layers
- Monitor connection lifecycle and performance
- Trace requests from client applications through the proxy to the database
- Correlate database operations with application traces

### Metrics via Prometheus Exporter
OJP exposes operational metrics through a Prometheus-compatible endpoint, providing insights into:
- Connection pool utilization and health
- Query execution times and throughput
- Error rates and connection failures
- gRPC communication metrics
- Database-specific performance indicators

## Accessing Telemetry Data

### Prometheus Metrics
Metrics are exposed via HTTP endpoint and can be scraped by Prometheus:
- **Default endpoint**: `http://localhost:9090/metrics`
- **Format**: Prometheus text-based exposition format
- **Update frequency**: Real-time metrics updated on each operation

To access metrics:
1. Configure Prometheus to scrape the OJP server metrics endpoint
2. Set up Grafana dashboards to visualize the metrics
3. Create alerts based on connection pool health and performance thresholds

### OpenTelemetry Traces
Traces can be exported to various backends supported by OpenTelemetry:
- **Jaeger**: For distributed tracing visualization
- **Zipkin**: Alternative tracing backend
- **OTLP receivers**: Any OpenTelemetry Protocol compatible system
- **Cloud providers**: AWS X-Ray, Google Cloud Trace, Azure Monitor

## Configuration Options

The telemetry system can be configured through the OJP server configuration file. Below is a placeholder YAML configuration example:

```yaml
telemetry:
  # Prometheus metrics configuration
  prometheus:
    enabled: true
    port: 9090
    endpoint: "/metrics"
    update_interval: "1s"
    
  # OpenTelemetry tracing configuration
  tracing:
    enabled: true
    service_name: "ojp-server"
    service_version: "0.0.4-alpha"
    
    # Trace exporters
    exporters:
      # OTLP exporter for OpenTelemetry collectors
      otlp:
        enabled: true
        endpoint: "http://localhost:4317"
        protocol: "grpc"
        
      # Jaeger exporter
      jaeger:
        enabled: false
        endpoint: "http://localhost:14250"
        
      # Zipkin exporter
      zipkin:
        enabled: false
        endpoint: "http://localhost:9411/api/v2/spans"
    
    # Sampling configuration
    sampling:
      type: "probabilistic"
      rate: 1.0  # Sample 100% of traces (adjust for production)
      
    # Resource attributes
    resource:
      attributes:
        environment: "development"
        deployment: "local"

  # Metrics collection settings
  metrics:
    # Connection pool metrics
    connection_pool:
      enabled: true
      include_database_labels: true
      
    # gRPC metrics
    grpc:
      enabled: true
      include_method_labels: true
      
    # SQL operation metrics
    sql_operations:
      enabled: true
      track_slow_queries: true
      slow_query_threshold: "1s"
```

## Integration Examples

### With Prometheus and Grafana
1. Configure Prometheus to scrape OJP metrics
2. Import OJP Grafana dashboard templates
3. Set up alerts for connection pool exhaustion and high error rates

### With Jaeger for Distributed Tracing
1. Deploy Jaeger backend
2. Configure OJP to export traces to Jaeger
3. Use Jaeger UI to visualize end-to-end database operation traces

### With OpenTelemetry Collector
1. Deploy OpenTelemetry Collector
2. Configure OJP to export to OTLP endpoint
3. Route traces and metrics to multiple backends through the collector

## Best Practices

- **Production Sampling**: Reduce trace sampling rate in production to minimize overhead
- **Metric Retention**: Configure appropriate retention policies for metrics storage
- **Security**: Ensure telemetry endpoints are properly secured in production environments
- **Performance**: Monitor the performance impact of telemetry collection on the proxy

## Troubleshooting

Common telemetry-related issues and solutions:

- **Missing metrics**: Verify Prometheus endpoint accessibility and scrape configuration
- **No traces**: Check OpenTelemetry exporter configuration and backend connectivity
- **High overhead**: Adjust sampling rates and disable unnecessary metric collection
- **Connection issues**: Verify telemetry backend endpoints and network connectivity