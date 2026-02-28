package com.cisco.wccai.grpc.server.interceptors;

import io.grpc.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class  AuthorizationServerInterceptor implements ServerInterceptor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationServerInterceptor.class);
    @Getter
    private static final Map<String,String> tokenMap = new HashMap<>();
    private final Map<String, String> mdcContext = new HashMap<>();
    public static final String TRACKING_ID = "trackingId";
    public static final String TOKEN = "token";
    public static final String ORG_ID = "org_id";

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> serverCall, Metadata metadata,
                                                                 ServerCallHandler<ReqT, RespT> serverCallHandler) {

        // Skip authorization for gRPC reflection service and Health service
        String methodName = serverCall.getMethodDescriptor().getFullMethodName();
        if (methodName.startsWith("grpc.reflection.v1alpha.ServerReflection") || 
            methodName.startsWith("grpc.reflection.v1.ServerReflection") ||
            methodName.startsWith("com.cisco.wcc.ccai.v1.Health")) {
            return serverCallHandler.startCall(serverCall, metadata);
        }

        String trackingId = metadata.get(Metadata.Key.of(TRACKING_ID, Metadata.ASCII_STRING_MARSHALLER));
        updateMdcContext(TRACKING_ID, trackingId);

        // Extract the Authorization header
        final String authHeader = metadata.get(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
        try {
            String token = AuthorizationHandlerFactory.extractToken(authHeader);
 
            // Update MDC context
            updateMdcContext(TOKEN, token);

            // Use AuthorizationFactory to determine the handler and validate the token
            AuthorizationHandler handler = AuthorizationHandlerFactory.getAuthorizationHandler(token);
            boolean isAuthorized = handler.validateToken(token);

            if (isAuthorized) {
                LOGGER.info("Token validation successful.");

                    updateMdcContext(ORG_ID,"org123");//replace the org id with your org id
            } else {
                throw new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Token validation failed."));
            }
        } catch (Exception e) {
            LOGGER.error("Authorization failed: {}", e.getMessage());
            throw new StatusRuntimeException(Status.UNAUTHENTICATED.withDescription("Authorization failed: " + e.getMessage()));
        } finally {
            MDC.setContextMap(mdcContext);
        }
        return serverCallHandler.startCall(serverCall, metadata);
    }

    private void updateMdcContext(String key, String value) {
        if(Objects.nonNull(key) && Objects.nonNull(value)){
            mdcContext.put(key, value);
            LOGGER.info("Setting MDC context key:{}, value:{}", key, value);
        }
    }
}
