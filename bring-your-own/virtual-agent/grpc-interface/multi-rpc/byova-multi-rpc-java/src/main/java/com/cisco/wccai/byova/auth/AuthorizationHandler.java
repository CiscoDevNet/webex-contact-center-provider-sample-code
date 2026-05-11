package com.cisco.wccai.byova.auth;

/**
 * Strategy interface for validating an inbound authorization token.
 *
 * <p>Implementations are selected by {@link AuthorizationHandlerFactory} based on the format of
 * the token presented in the request metadata.
 */
public interface AuthorizationHandler {

    /**
     * Validate the supplied token.
     *
     * @param token the raw token string (no {@code Bearer } prefix)
     * @return {@code true} when the token is valid for this server
     * @throws AccessTokenException if validation cannot complete (parse error, JWKS unreachable,
     *                              signature mismatch, expired/invalid claims, etc.)
     */
    boolean validateToken(String token) throws AccessTokenException;
}
