package org.openjproxy.grpc.client;

import com.openjproxy.grpc.xa.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * Client wrapper for XA gRPC service calls.
 */
@Slf4j
public class XaServiceGrpcClient implements XaService {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1059;
    
    private final ManagedChannel channel;
    private final XaServiceGrpc.XaServiceBlockingStub blockingStub;

    public XaServiceGrpcClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }

    public XaServiceGrpcClient(String host, int port) {
        log.debug("Creating XaServiceGrpcClient for {}:{}", host, port);
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .maxInboundMessageSize(100 * 1024 * 1024) // 100MB
                .build();
        this.blockingStub = XaServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public XaConnectionResponse xaConnect(XaConnectionRequest request) {
        log.debug("xaConnect: {}", request.getUrl());
        try {
            return blockingStub.xaConnect(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaConnect", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaResponse xaStart(XaStartRequest request) {
        log.debug("xaStart: xid={}, flags={}", request.getXid(), request.getFlags());
        try {
            return blockingStub.xaStart(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaStart", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaResponse xaEnd(XaEndRequest request) {
        log.debug("xaEnd: xid={}, flags={}", request.getXid(), request.getFlags());
        try {
            return blockingStub.xaEnd(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaEnd", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaPrepareResponse xaPrepare(XaPrepareRequest request) {
        log.debug("xaPrepare: xid={}", request.getXid());
        try {
            return blockingStub.xaPrepare(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaPrepare", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaResponse xaCommit(XaCommitRequest request) {
        log.debug("xaCommit: xid={}, onePhase={}", request.getXid(), request.getOnePhase());
        try {
            return blockingStub.xaCommit(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaCommit", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaResponse xaRollback(XaRollbackRequest request) {
        log.debug("xaRollback: xid={}", request.getXid());
        try {
            return blockingStub.xaRollback(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaRollback", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaRecoverResponse xaRecover(XaRecoverRequest request) {
        log.debug("xaRecover: flag={}", request.getFlag());
        try {
            return blockingStub.xaRecover(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaRecover", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaResponse xaForget(XaForgetRequest request) {
        log.debug("xaForget: xid={}", request.getXid());
        try {
            return blockingStub.xaForget(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaForget", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaSetTransactionTimeoutResponse xaSetTransactionTimeout(XaSetTransactionTimeoutRequest request) {
        log.debug("xaSetTransactionTimeout: seconds={}", request.getSeconds());
        try {
            return blockingStub.xaSetTransactionTimeout(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaSetTransactionTimeout", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaGetTransactionTimeoutResponse xaGetTransactionTimeout(XaGetTransactionTimeoutRequest request) {
        log.debug("xaGetTransactionTimeout");
        try {
            return blockingStub.xaGetTransactionTimeout(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaGetTransactionTimeout", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaIsSameRMResponse xaIsSameRM(XaIsSameRMRequest request) {
        log.debug("xaIsSameRM");
        try {
            return blockingStub.xaIsSameRM(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaIsSameRM", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    @Override
    public XaResponse xaClose(XaCloseRequest request) {
        log.debug("xaClose: xaSessionUUID={}", request.getXaSessionUUID());
        try {
            return blockingStub.xaClose(request);
        } catch (io.grpc.StatusRuntimeException e) {
            log.error("Error in xaClose", e);
            try {
                throw GrpcExceptionHandler.handle(e);
            } catch (java.sql.SQLException sqle) {
                throw new RuntimeException(sqle);
            }
        }
    }

    public void shutdown() throws InterruptedException {
        log.debug("Shutting down XaServiceGrpcClient");
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
