package com.cisco.wccai.forking.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Extracts (or generates) an rpc correlation id from inbound metadata and makes it available to
 * downstream services via the gRPC {@link Context}.
 */
@Slf4j
@Component
public class MetadataInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        String rpcId = GrpcContextHelper.extractOrGenerateRpcId(headers);
        log.info("Processing gRPC call {} with rpcId: {}", call.getMethodDescriptor().getFullMethodName(), rpcId);

        Context ctx = GrpcContextHelper.withRpcId(rpcId);
        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
