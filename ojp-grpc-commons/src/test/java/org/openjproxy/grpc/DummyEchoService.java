package org.openjproxy.grpc;

import org.openjproxy.grpc.EchoRequest;
import org.openjproxy.grpc.EchoResponse;
import org.openjproxy.grpc.EchoServiceGrpc;
import io.grpc.stub.StreamObserver;

// Just echoing back the message
public class DummyEchoService extends EchoServiceGrpc.EchoServiceImplBase {
	@Override
	public void echo(EchoRequest request, StreamObserver<EchoResponse> responseObserver) {
		responseObserver.onNext(EchoResponse.newBuilder()
				.setMessage(request.getMessage())
				.build());
		responseObserver.onCompleted();
	}
}
