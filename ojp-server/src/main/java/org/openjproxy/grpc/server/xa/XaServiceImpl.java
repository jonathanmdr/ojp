package org.openjproxy.grpc.server.xa;

import com.google.protobuf.ByteString;
import com.openjproxy.grpc.xa.*;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openjproxy.grpc.SerializationHandler;
import org.openjproxy.grpc.server.GrpcExceptionHandler;
import org.openjproxy.grpc.server.XidImpl;
import org.openjproxy.grpc.server.pool.ConnectionPoolConfigurer;
import org.openjproxy.grpc.server.pool.DataSourceConfigurationManager;
import org.openjproxy.grpc.server.utils.ConnectionHashGenerator;
import org.openjproxy.grpc.server.utils.DriverUtils;
import org.openjproxy.grpc.server.utils.UrlParser;

import javax.sql.XAConnection;
import javax.sql.XADataSource;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * Server-side implementation of the XA service.
 * Handles XA operations by delegating to underlying database XA resources.
 */
@Slf4j
@RequiredArgsConstructor
public class XaServiceImpl extends XaServiceGrpc.XaServiceImplBase {

    private final XaSessionManager xaSessionManager;
    private final Map<String, XADataSource> xaDataSourceMap = new ConcurrentHashMap<>();

    static {
        DriverUtils.registerDrivers();
    }

    @Override
    public void xaConnect(XaConnectionRequest request, StreamObserver<XaConnectionResponse> responseObserver) {
        log.info("xaConnect: url={}", request.getUrl());
        
        try {
            String connHash = ConnectionHashGenerator.hashConnectionDetails(
                com.openjproxy.grpc.ConnectionDetails.newBuilder()
                    .setUrl(request.getUrl())
                    .setUser(request.getUser())
                    .setPassword(request.getPassword())
                    .setClientUUID(request.getClientUUID())
                    .setProperties(request.getProperties())
                    .build()
            );

            // Get or create XA DataSource
            XADataSource xaDataSource = getOrCreateXADataSource(connHash, request);

            // Get XA connection from the data source
            XAConnection xaConnection = xaDataSource.getXAConnection(request.getUser(), request.getPassword());
            
            // Create and register XA session
            XaSession xaSession = new XaSession(xaConnection, connHash, request.getClientUUID());
            xaSessionManager.registerSession(xaSession);

            // Send response
            XaConnectionResponse response = XaConnectionResponse.newBuilder()
                    .setXaSessionUUID(xaSession.getXaSessionUUID())
                    .setConnHash(connHash)
                    .setClientUUID(request.getClientUUID())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
            log.info("XA connection established: {}", xaSession.getXaSessionUUID());

        } catch (Exception e) {
            log.error("Error in xaConnect", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    @Override
    public void xaStart(XaStartRequest request, StreamObserver<XaResponse> responseObserver) {
        log.debug("xaStart: xaSessionUUID={}, xid={}, flags={}", 
                request.getXaSessionUUID(), request.getXid(), request.getFlags());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            Xid xid = convertXid(request.getXid());
            
            session.getXaResource().start(xid, request.getFlags());
            
            XaResponse response = XaResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("XA start successful")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaStart", e);
            handleXAException(responseObserver, e);
        }
    }

    @Override
    public void xaEnd(XaEndRequest request, StreamObserver<XaResponse> responseObserver) {
        log.debug("xaEnd: xaSessionUUID={}, xid={}, flags={}", 
                request.getXaSessionUUID(), request.getXid(), request.getFlags());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            Xid xid = convertXid(request.getXid());
            
            session.getXaResource().end(xid, request.getFlags());
            
            XaResponse response = XaResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("XA end successful")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaEnd", e);
            handleXAException(responseObserver, e);
        }
    }

    @Override
    public void xaPrepare(XaPrepareRequest request, StreamObserver<XaPrepareResponse> responseObserver) {
        log.debug("xaPrepare: xaSessionUUID={}, xid={}", 
                request.getXaSessionUUID(), request.getXid());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            Xid xid = convertXid(request.getXid());
            
            int result = session.getXaResource().prepare(xid);
            
            XaPrepareResponse response = XaPrepareResponse.newBuilder()
                    .setResult(result)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaPrepare", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    @Override
    public void xaCommit(XaCommitRequest request, StreamObserver<XaResponse> responseObserver) {
        log.debug("xaCommit: xaSessionUUID={}, xid={}, onePhase={}", 
                request.getXaSessionUUID(), request.getXid(), request.getOnePhase());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            Xid xid = convertXid(request.getXid());
            
            session.getXaResource().commit(xid, request.getOnePhase());
            
            XaResponse response = XaResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("XA commit successful")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaCommit", e);
            handleXAException(responseObserver, e);
        }
    }

    @Override
    public void xaRollback(XaRollbackRequest request, StreamObserver<XaResponse> responseObserver) {
        log.debug("xaRollback: xaSessionUUID={}, xid={}", 
                request.getXaSessionUUID(), request.getXid());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            Xid xid = convertXid(request.getXid());
            
            session.getXaResource().rollback(xid);
            
            XaResponse response = XaResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("XA rollback successful")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaRollback", e);
            handleXAException(responseObserver, e);
        }
    }

    @Override
    public void xaRecover(XaRecoverRequest request, StreamObserver<XaRecoverResponse> responseObserver) {
        log.debug("xaRecover: xaSessionUUID={}, flag={}", 
                request.getXaSessionUUID(), request.getFlag());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            
            Xid[] xids = session.getXaResource().recover(request.getFlag());
            
            XaRecoverResponse response = XaRecoverResponse.newBuilder()
                    .addAllXids(Arrays.stream(xids)
                            .map(this::convertXidToProto)
                            .collect(Collectors.toList()))
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaRecover", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    @Override
    public void xaForget(XaForgetRequest request, StreamObserver<XaResponse> responseObserver) {
        log.debug("xaForget: xaSessionUUID={}, xid={}", 
                request.getXaSessionUUID(), request.getXid());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            Xid xid = convertXid(request.getXid());
            
            session.getXaResource().forget(xid);
            
            XaResponse response = XaResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("XA forget successful")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaForget", e);
            handleXAException(responseObserver, e);
        }
    }

    @Override
    public void xaSetTransactionTimeout(XaSetTransactionTimeoutRequest request, 
                                        StreamObserver<XaSetTransactionTimeoutResponse> responseObserver) {
        log.debug("xaSetTransactionTimeout: xaSessionUUID={}, seconds={}", 
                request.getXaSessionUUID(), request.getSeconds());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            
            boolean success = session.getXaResource().setTransactionTimeout(request.getSeconds());
            if (success) {
                session.setTransactionTimeout(request.getSeconds());
            }
            
            XaSetTransactionTimeoutResponse response = XaSetTransactionTimeoutResponse.newBuilder()
                    .setSuccess(success)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaSetTransactionTimeout", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    @Override
    public void xaGetTransactionTimeout(XaGetTransactionTimeoutRequest request, 
                                        StreamObserver<XaGetTransactionTimeoutResponse> responseObserver) {
        log.debug("xaGetTransactionTimeout: xaSessionUUID={}", request.getXaSessionUUID());
        
        try {
            XaSession session = xaSessionManager.getSession(request.getXaSessionUUID());
            
            int timeout = session.getXaResource().getTransactionTimeout();
            
            XaGetTransactionTimeoutResponse response = XaGetTransactionTimeoutResponse.newBuilder()
                    .setSeconds(timeout)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaGetTransactionTimeout", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    @Override
    public void xaIsSameRM(XaIsSameRMRequest request, StreamObserver<XaIsSameRMResponse> responseObserver) {
        log.debug("xaIsSameRM: xaSessionUUID={}, otherXaSessionUUID={}", 
                request.getXaSessionUUID(), request.getOtherXaSessionUUID());
        
        try {
            XaSession session1 = xaSessionManager.getSession(request.getXaSessionUUID());
            XaSession session2 = xaSessionManager.getSession(request.getOtherXaSessionUUID());
            
            boolean isSame = session1.getXaResource().isSameRM(session2.getXaResource());
            
            XaIsSameRMResponse response = XaIsSameRMResponse.newBuilder()
                    .setIsSame(isSame)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaIsSameRM", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    @Override
    public void xaClose(XaCloseRequest request, StreamObserver<XaResponse> responseObserver) {
        log.debug("xaClose: xaSessionUUID={}", request.getXaSessionUUID());
        
        try {
            xaSessionManager.closeSession(request.getXaSessionUUID());
            
            XaResponse response = XaResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("XA connection closed")
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error in xaClose", e);
            SQLException sqlException = (e instanceof SQLException) ? (SQLException) e : new SQLException(e);
            GrpcExceptionHandler.sendSQLExceptionMetadata(sqlException, responseObserver);
        }
    }

    /**
     * Get or create an XA data source for the given connection details.
     */
    private XADataSource getOrCreateXADataSource(String connHash, XaConnectionRequest request) throws SQLException {
        XADataSource xaDataSource = xaDataSourceMap.get(connHash);
        
        if (xaDataSource == null) {
            synchronized (xaDataSourceMap) {
                xaDataSource = xaDataSourceMap.get(connHash);
                if (xaDataSource == null) {
                    xaDataSource = createXADataSource(request);
                    xaDataSourceMap.put(connHash, xaDataSource);
                    log.info("Created new XA DataSource for connHash: {}", connHash);
                }
            }
        }
        
        return xaDataSource;
    }

    /**
     * Create an XA data source based on the connection request.
     * This uses HikariCP's XA support or creates a wrapper.
     */
    private XADataSource createXADataSource(XaConnectionRequest request) throws SQLException {
        try {
            String url = UrlParser.parseUrl(request.getUrl());
            
            // Determine database type and create appropriate XA DataSource
            // PostgreSQL example
            if (url.contains("postgresql")) {
                return createPostgreSQLXADataSource(url, request);
            }
            // Add support for other databases as needed
            else {
                throw new SQLException("XA not supported for database: " + url);
            }
            
        } catch (Exception e) {
            throw new SQLException("Failed to create XA DataSource", e);
        }
    }

    /**
     * Create PostgreSQL XA DataSource.
     */
    private XADataSource createPostgreSQLXADataSource(String url, XaConnectionRequest request) throws SQLException {
        try {
            // Use PostgreSQL's XA DataSource
            org.postgresql.xa.PGXADataSource xaDataSource = new org.postgresql.xa.PGXADataSource();
            
            // Parse URL to extract connection properties
            // Simple parser for postgresql://host:port/database
            String cleanUrl = url.replace("jdbc:postgresql://", "");
            String[] parts = cleanUrl.split("/");
            if (parts.length >= 1) {
                String[] hostPort = parts[0].split(":");
                xaDataSource.setServerNames(new String[]{hostPort[0]});
                if (hostPort.length > 1) {
                    xaDataSource.setPortNumbers(new int[]{Integer.parseInt(hostPort[1])});
                }
            }
            if (parts.length >= 2) {
                xaDataSource.setDatabaseName(parts[1].split("\\?")[0]);
            }
            
            xaDataSource.setUser(request.getUser());
            xaDataSource.setPassword(request.getPassword());
            
            return xaDataSource;
            
        } catch (Exception e) {
            throw new SQLException("Failed to create PostgreSQL XA DataSource", e);
        }
    }

    /**
     * Convert protobuf Xid to javax.transaction.xa.Xid.
     */
    private Xid convertXid(XidProto xidProto) {
        return new XidImpl(
                xidProto.getFormatId(),
                xidProto.getGlobalTransactionId().toByteArray(),
                xidProto.getBranchQualifier().toByteArray()
        );
    }

    /**
     * Convert javax.transaction.xa.Xid to protobuf Xid.
     */
    private XidProto convertXidToProto(Xid xid) {
        return XidProto.newBuilder()
                .setFormatId(xid.getFormatId())
                .setGlobalTransactionId(ByteString.copyFrom(xid.getGlobalTransactionId()))
                .setBranchQualifier(ByteString.copyFrom(xid.getBranchQualifier()))
                .build();
    }

    /**
     * Handle XA exceptions and convert to gRPC responses.
     */
    private void handleXAException(StreamObserver<XaResponse> responseObserver, Exception e) {
        String message = e.getMessage();
        if (e instanceof XAException) {
            XAException xae = (XAException) e;
            message = "XA Error (code " + xae.errorCode + "): " + xae.getMessage();
        }
        
        XaResponse response = XaResponse.newBuilder()
                .setSuccess(false)
                .setMessage(message)
                .build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
