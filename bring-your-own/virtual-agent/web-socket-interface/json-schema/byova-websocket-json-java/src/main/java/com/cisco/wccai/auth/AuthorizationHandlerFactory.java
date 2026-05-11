package com.cisco.wccai.auth;

import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves the appropriate {@link AuthorizationHandler} for the inbound token. Today only the
 * Cisco JWS/JWT format is recognised; OAuth2 bearer tokens are rejected explicitly so that
 * misconfigurations fail loudly instead of silently bypassing validation.
 */
@Slf4j
@Component
public class AuthorizationHandlerFactory {

    public enum AuthTokenType {
        /** Cisco JWS/JWT minted by the Identity Broker. */
        JWT,
        /** Opaque OAuth2 bearer token (not yet supported). */
        OAUTH2,
        /** No authorization token presented. */
        NOAUTH
    }

    private final AuthProperties authProperties;

    public AuthorizationHandlerFactory(AuthProperties authProperties) {
        this.authProperties = authProperties;
    }

    public AuthorizationHandler getAuthorizationHandler(String token) throws AccessTokenException {
        AuthTokenType tokenType = getAuthTokenType(token);
        return switch (tokenType) {
            case JWT -> {
                log.debug("Resolved JWT authorization handler");
                yield new JWTAuthorizationHandler(authProperties);
            }
            case OAUTH2, NOAUTH -> throw new AccessTokenException("Invalid authorization token");
        };
    }

    /**
     * Extract the bearer token from the raw {@code Authorization} header. Accepts both
     * {@code "Bearer <token>"} and bare token formats (both are seen in practice).
     */
    public static String extractToken(String authHeader) {
        if (authHeader == null || authHeader.isBlank()) {
            return "";
        }
        String trimmed = authHeader.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return trimmed.substring(7).trim();
        }
        return trimmed;
    }

    private static AuthTokenType getAuthTokenType(String token) {
        if (token == null || token.isEmpty()) {
            return AuthTokenType.NOAUTH;
        }
        if (isJWT(token)) {
            return AuthTokenType.JWT;
        }
        return AuthTokenType.OAUTH2;
    }

    /**
     * We cannot rely on the number of '.' separators alone (OAuth2 tokens can also contain dots).
     * The {@code token_type} claim, when present and equal to {@code Bearer}, identifies the
     * token as an opaque OAuth2 token rather than a JWS/JWT.
     */
    private static boolean isJWT(String token) {
        try {
            Object tokenType = SignedJWT.parse(token).getJWTClaimsSet().getClaim("token_type");
            return tokenType == null || !"Bearer".equalsIgnoreCase(tokenType.toString());
        } catch (Exception ex) {
            return false;
        }
    }
}
