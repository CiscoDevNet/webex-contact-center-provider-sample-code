package com.cisco.wccai.auth;

/**
 * Thrown when an inbound JWS/JWT token cannot be validated (parse failure, signature mismatch,
 * expired token, missing/invalid claims, JWKS lookup failure, etc.).
 */
public class AccessTokenException extends Exception {

    public AccessTokenException(String message) {
        super(message);
    }

    public AccessTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
