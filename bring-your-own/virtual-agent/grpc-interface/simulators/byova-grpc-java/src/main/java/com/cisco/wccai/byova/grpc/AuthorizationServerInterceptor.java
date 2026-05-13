package com.cisco.wccai.byova.grpc;

import com.cisco.wccai.byova.auth.AccessTokenException;
import com.cisco.wccai.byova.auth.AuthorizationHandler;
import com.cisco.wccai.byova.auth.AuthorizationHandlerFactory;
import com.cisco.wccai.byova.config.AuthProperties;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * gRPC {@link ServerInterceptor} that validates the JWS/JWT presented in the {@code
 * authorization} metadata header on every inbound call.
 *
 * <p>The token is parsed, signature-verified against the issuer's JWKS, checked for required
 * claims, and bound to the configured datasource URL/schema UUID. Any failure terminates the
 * call with {@link Status#UNAUTHENTICATED}.
 *
 * <p>Validation can be disabled by setting {@code auth.enabled=false} (intended for local
 * development against an unauthenticated client only).
 */
@Slf4j
@Component
public class AuthorizationServerInterceptor implements ServerInterceptor {

    public static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
    public static final Metadata.Key<String> TRACKING_ID_KEY =
            Metadata.Key.of("trackingId", Metadata.ASCII_STRING_MARSHALLER);

    private final AuthorizationHandlerFactory authorizationHandlerFactory;
    private final AuthProperties properties;

    public AuthorizationServerInterceptor(
            AuthorizationHandlerFactory authorizationHandlerFactory, AuthProperties properties) {
        this.authorizationHandlerFactory = authorizationHandlerFactory;
        this.properties = properties;
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> serverCall,
            Metadata metadata,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {

        if (!properties.enabled()) {
            log.debug("Authorization disabled (auth.enabled=false); skipping JWT validation");
            return serverCallHandler.startCall(serverCall, metadata);
        }

        String trackingId = metadata.get(TRACKING_ID_KEY);
        String authHeader = metadata.get(AUTHORIZATION_KEY);
        try {
            String token = AuthorizationHandlerFactory.extractToken(authHeader);
            AuthorizationHandler handler = authorizationHandlerFactory.getAuthorizationHandler(token);
            if (!handler.validateToken(token)) {
                throw new StatusRuntimeException(
                        Status.UNAUTHENTICATED.withDescription("Token validation failed."));
            }
            log.info("Token validation successful for trackingId={}", trackingId);
        } catch (AccessTokenException | RuntimeException e) {
            log.error("Authorization failed for trackingId={}: {}", trackingId, e.getMessage());
            throw new StatusRuntimeException(
                    Status.UNAUTHENTICATED.withDescription("Authorization failed: " + e.getMessage()));
        }
        return serverCallHandler.startCall(serverCall, metadata);
    }
}
