package org.openjdbcproxy.grpc.server;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.prometheus.PrometheusHttpServer;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;

/**
 * OJP Server Telemetry Configuration for OpenTelemetry with Prometheus Exporter.
 * This class provides methods to create a GrpcTelemetry instance with Prometheus metrics.
 */
public class OjpServerTelemetry {

	private static final int DEFAULT_PROMETHEUS_PORT = 9090;

	public GrpcTelemetry createGrpcTelemetry() {
		return createGrpcTelemetry(DEFAULT_PROMETHEUS_PORT);
	}

	public GrpcTelemetry createGrpcTelemetry(int prometheusPort) {
		PrometheusHttpServer prometheusServer = PrometheusHttpServer.builder()
				.setPort(prometheusPort)
				.build();

		OpenTelemetry openTelemetry = OpenTelemetrySdk.builder()
				.setMeterProvider(
						SdkMeterProvider.builder()
								.registerMetricReader(prometheusServer)
								.build())
				.build();

		return GrpcTelemetry.create(openTelemetry);
	}
}
