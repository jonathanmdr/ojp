package org.openjdbcproxy.grpc.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcTelemetry;
import org.openjdbcproxy.constants.CommonConstants;

import java.io.IOException;

public class GrpcServer {

    public static void main(String[] args) throws IOException, InterruptedException {
        OjpServerTelemetry ojpServerTelemetry = new OjpServerTelemetry();
        GrpcTelemetry grpcTelemetry = ojpServerTelemetry.createGrpcTelemetry();

        Server server = ServerBuilder
                .forPort(CommonConstants.DEFAULT_PORT_NUMBER)
                .addService(new StatementServiceImpl(
                        new SessionManagerImpl(),
                        new CircuitBreaker(60000)//TODO pass as parameter currently
                ))
                .intercept(grpcTelemetry.newServerInterceptor())
                .build();

        server.start();
        server.awaitTermination();
    }
}
