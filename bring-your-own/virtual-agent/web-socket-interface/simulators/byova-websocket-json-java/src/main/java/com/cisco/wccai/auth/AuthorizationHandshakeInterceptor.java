package com.cisco.wccai.auth;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Spring {@link HandshakeInterceptor} that validates the JWS/JWT presented in the {@code
 * Authorization} HTTP header during the WebSocket upgrade.
 *
 * <p>This is the WebSocket equivalent of the gRPC {@code AuthorizationServerInterceptor} in the
 * BYoVA gRPC sample: the token is parsed, signature-verified against the issuer's JWKS,
 * checked for required claims, and bound to the configured datasource URL/schema UUID. A failure
 * aborts the handshake with HTTP 401, so no WebSocket session is ever opened with an invalid
 * caller.
 *
 * <p>Validation can be disabled by setting {@code auth.enabled=false} (intended for local
 * development against an unauthenticated client only).
 */
@Slf4j
@Component
public class AuthorizationHandshakeInterceptor implements HandshakeInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TRACKING_ID_HEADER = "trackingId";
    private static final String TRACKING_ID_ATTR = "trackingId";
    private static final String SUBJECT_ATTR = "subject";

    private final AuthorizationHandlerFactory authorizationHandlerFactory;
    private final AuthProperties properties;

    public AuthorizationHandshakeInterceptor(
            AuthorizationHandlerFactory authorizationHandlerFactory, AuthProperties properties) {
        this.authorizationHandlerFactory = authorizationHandlerFactory;
        this.properties = properties;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String trackingId = request.getHeaders().getFirst(TRACKING_ID_HEADER);
        if (trackingId != null) {
            attributes.put(TRACKING_ID_ATTR, trackingId);
        }

        if (!properties.enabled()) {
            log.debug("Authorization disabled (auth.enabled=false); skipping JWT validation for {}",
                    request.getURI());
            return true;
        }

        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        try {
            String token = AuthorizationHandlerFactory.extractToken(authHeader);
            AuthorizationHandler handler = authorizationHandlerFactory.getAuthorizationHandler(token);
            if (!handler.validateToken(token)) {
                rejectHandshake(response, "Token validation failed.", trackingId);
                return false;
            }
            attributes.put(SUBJECT_ATTR, "authenticated");
            log.info("WebSocket handshake authorized for {} (trackingId={})", request.getURI(), trackingId);
            return true;
        } catch (AccessTokenException | RuntimeException e) {
            rejectHandshake(response, "Authorization failed: " + e.getMessage(), trackingId);
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            log.warn("WebSocket handshake completed with error for {}: {}", request.getURI(), exception.getMessage());
        }
    }

    private void rejectHandshake(ServerHttpResponse response, String reason, String trackingId) {
        log.error("WebSocket handshake rejected (trackingId={}): {}", trackingId, reason);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
    }
}
