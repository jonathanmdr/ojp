package org.openjdbcproxy.jdbc;

import com.google.common.util.concurrent.SettableFuture;
import com.openjdbcproxy.grpc.LobDataBlock;
import com.openjdbcproxy.grpc.LobReference;
import com.openjdbcproxy.grpc.LobType;
import io.grpc.StatusRuntimeException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.openjdbcproxy.grpc.client.StatementService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.openjdbcproxy.constants.CommonConstants.MAX_LOB_DATA_BLOCK_SIZE;
import static org.openjdbcproxy.grpc.client.GrpcExceptionHandler.handle;

@Slf4j
public class Lob {

    protected final Connection connection;
    protected final LobService lobService;
    protected final StatementService statementService;
    protected final SettableFuture<LobReference> lobReference = SettableFuture.create();

    public Lob(Connection connection, LobService lobService, StatementService statementService, LobReference lobReference) {
        log.debug("Lob constructor called");
        this.connection = connection;
        this.lobService = lobService;
        this.statementService = statementService;
        if (lobReference != null) {
            this.lobReference.set(lobReference);
        }
    }

    public String getUUID() {
        log.debug("getUUID called");
        try {
            return (this.lobReference != null) ? this.lobReference.get().getUuid() : null;
        } catch (InterruptedException e) {
            log.error("InterruptedException in getUUID", e);
            throw new RuntimeException(e);//TODO review
        } catch (ExecutionException e) {
            log.error("ExecutionException in getUUID", e);
            throw new RuntimeException(e);//TODO review
        }
    }

    public long length() throws SQLException {
        log.debug("length called");
        return 0; //TODO implement
    }

    protected OutputStream setBinaryStream(LobType lobType, long pos) {
        log.debug("setBinaryStream called: {}, {}", lobType, pos);
        try {
            //connect the pipes. Makes the OutputStream written by the caller feed into the InputStream read by the sender.
            PipedInputStream in = new PipedInputStream();
            PipedOutputStream out = new PipedOutputStream(in);

            CompletableFuture.supplyAsync(() -> {
                try {
                    this.lobReference.set(this.lobService.sendBytes(lobType, pos, in));
                } catch (SQLException e) {
                    log.error("SQLException in setBinaryStream async - sendBytes", e);
                    throw new RuntimeException(e);
                }
                //Refresh Session object.
                try {
                    this.connection.setSession(this.lobReference.get().getSession());
                } catch (InterruptedException e) {
                    log.error("InterruptedException in setBinaryStream async - setSession", e);
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    log.error("ExecutionException in setBinaryStream async - setSession", e);
                    throw new RuntimeException(e);
                }
                return null;
            });

            return out;
        } catch (Exception e) {
            log.error("Exception in setBinaryStream", e);
            throw new RuntimeException(e);
        }
    }

    protected LobReference sendBinaryStream(LobType lobType, InputStream inputStream, Map<Integer, Object> metadata) {
        log.debug("sendBinaryStream called: {}, <InputStream>, <metadata>", lobType);
        try {
            try {
                this.lobReference.set(this.lobService.sendBytes(lobType, 1, inputStream, metadata));
            } catch (SQLException e) {
                log.error("SQLException in sendBinaryStream - sendBytes", e);
                throw new RuntimeException(e);
            }
            //Refresh Session object. Will wait until lobReference is set to progress.
            this.connection.setSession(this.lobReference.get().getSession());
            return this.lobReference.get();
        } catch (Exception e) {
            log.error("Exception in sendBinaryStream", e);
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    protected void haveLobReferenceValidation() throws SQLException {
        log.debug("haveLobReferenceValidation called");
        if (this.lobReference.get() == null) {
            log.error("No reference to a LOB object found.");
            throw new SQLException("No reference to a LOB object found.");
        }
    }

    protected InputStream getBinaryStream(long pos, long length) throws SQLException {
        log.debug("getBinaryStream called: {}, {}", pos, length);
        try {
            this.haveLobReferenceValidation();

            return new InputStream() {
                private InputStream currentBlockInputStream;
                private long currentPos = pos - 1;//minus 1 because it will increment it in the loop

                @Override
                public int read() throws IOException {
                    int currentByte = this.currentBlockInputStream != null ? this.currentBlockInputStream.read() : -1;
                    int TWO_BLOCKS_SIZE = 2 * MAX_LOB_DATA_BLOCK_SIZE;
                    currentPos++;

                    if ((currentBlockInputStream == null || currentByte == -1)) {
                        // If we have no current block or reached end of current block, try to read more
                        if (currentPos <= length) {
                            //Read next 2 blocks
                            Iterator<LobDataBlock> dataBlocks = null;
                            try {
                                dataBlocks = statementService.readLob(lobReference.get(), currentPos, TWO_BLOCKS_SIZE);
                                this.currentBlockInputStream = lobService.parseReceivedBlocks(dataBlocks);
                                if (this.currentBlockInputStream != null) {
                                    currentByte = this.currentBlockInputStream.read();
                                }
                            } catch (SQLException e) {
                                log.error("SQLException in getBinaryStream InputStream.read() - readLob/parseReceivedBlocks", e);
                                throw new RuntimeException(e);
                            } catch (StatusRuntimeException e) {
                                try {
                                    throw handle(e);
                                } catch (SQLException ex) {
                                    log.error("SQLException in handle(StatusRuntimeException)", ex);
                                    throw new RuntimeException(ex);
                                }
                            } catch (Exception e) {
                                log.error("Exception in getBinaryStream InputStream.read()", e);
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    if (currentPos > length || currentByte == -1) {
                        return -1;//Finish stream if reached the length required or no more data
                    }

                    return currentByte;
                }
            };
        } catch (SQLException e) {
            log.error("SQLException in getBinaryStream", e);
            throw e;
        } catch (StatusRuntimeException e) {
            log.error("StatusRuntimeException in getBinaryStream", e);
            throw handle(e);
        } catch (Exception e) {
            log.error("Exception in getBinaryStream", e);
            throw new SQLException("Unable to read all bytes from LOB object: " + e.getMessage(), e);
        }
    }
}