package com.cisco.wccai.grpc.server;

import com.cisco.wcc.ccai.v1.HealthOuterClass;
import com.cisco.wcc.ccai.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HealthCheckImpl extends HealthGrpc.HealthImplBase {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckImpl.class);

    @Override
    public void check(HealthOuterClass.HealthCheckRequest request, StreamObserver<HealthOuterClass.HealthCheckResponse> responseObserver) {
        LOGGER.info("Health check request received for service: {}", request.getService());
        
        // Always respond to health checks, regardless of service field
        HealthOuterClass.HealthCheckResponse.ServingStatus status;
        
        if (GrpcServer.isServerIsRunning()) {
            status = HealthOuterClass.HealthCheckResponse.ServingStatus.SERVING;
            LOGGER.info("Health check response sent: SERVING");
        } else {
            status = HealthOuterClass.HealthCheckResponse.ServingStatus.NOT_SERVING;
            LOGGER.info("Health check response sent: NOT_SERVING");
        }
        
        HealthOuterClass.HealthCheckResponse response = HealthOuterClass.HealthCheckResponse.newBuilder()
                .setStatus(status)
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }


    @Override
    public void watch(HealthOuterClass.HealthCheckRequest request, StreamObserver<HealthOuterClass.HealthCheckResponse> responseObserver) {
        super.watch(request, responseObserver);
    }
}
