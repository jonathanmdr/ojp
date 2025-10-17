package org.openjproxy.grpc.client;

import com.openjproxy.grpc.xa.*;

/**
 * Service interface for XA operations.
 */
public interface XaService {
    XaConnectionResponse xaConnect(XaConnectionRequest request);
    XaResponse xaStart(XaStartRequest request);
    XaResponse xaEnd(XaEndRequest request);
    XaPrepareResponse xaPrepare(XaPrepareRequest request);
    XaResponse xaCommit(XaCommitRequest request);
    XaResponse xaRollback(XaRollbackRequest request);
    XaRecoverResponse xaRecover(XaRecoverRequest request);
    XaResponse xaForget(XaForgetRequest request);
    XaSetTransactionTimeoutResponse xaSetTransactionTimeout(XaSetTransactionTimeoutRequest request);
    XaGetTransactionTimeoutResponse xaGetTransactionTimeout(XaGetTransactionTimeoutRequest request);
    XaIsSameRMResponse xaIsSameRM(XaIsSameRMRequest request);
    XaResponse xaClose(XaCloseRequest request);
}
