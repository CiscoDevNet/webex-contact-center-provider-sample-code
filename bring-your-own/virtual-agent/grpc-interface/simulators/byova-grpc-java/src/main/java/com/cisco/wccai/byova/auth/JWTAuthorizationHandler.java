package com.cisco.wccai.byova.auth;

import com.cisco.wccai.byova.config.AuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * Validates an inbound JWS/JWT issued by the Webex Identity Broker.
 *
 * <p>Performs four checks on every token:
 * <ol>
 *   <li>Signature verification using the issuer's public JWKS (with in-memory caching).</li>
 *   <li>Expiration ({@code exp} claim).</li>
 *   <li>Required claim presence and an issuer ({@code iss}) allow-list.</li>
 *   <li>BYoVA datasource binding ({@code com.cisco.datasource.url} and
 *       {@code com.cisco.datasource.schema.uuid}) — confirms the token was minted for this
 *       service and schema.</li>
 * </ol>
 *
 * <p>The handler is intentionally not a Spring bean directly; it is created on demand by
 * {@link AuthorizationHandlerFactory} so that the JWKS cache can be shared across all calls.
 */
@Slf4j
public class JWTAuthorizationHandler implements AuthorizationHandler {

    private static final String DATASOURCE_URL_CLAIM = "com.cisco.datasource.url";
    private static final String DATASOURCE_SCHEMA_CLAIM = "com.cisco.datasource.schema.uuid";

    private static final Map<String, PublicKeyResponse> CACHED_PUBLIC_KEY_RESPONSE = new HashMap<>();
    private static final ReentrantLock CACHE_LOCK = new ReentrantLock();

    private final AuthProperties properties;
    private final long cacheDurationMillis;

    public JWTAuthorizationHandler(AuthProperties properties) {
        this.properties = properties;
        this.cacheDurationMillis = TimeUnit.MINUTES.toMillis(properties.publicKeyCacheMinutes());
        log.debug("JWTAuthorizationHandler initialized; expected datasource={}, schema={}",
                properties.datasourceUrl(), properties.datasourceSchemaUuid());
    }

    @Override
    public boolean validateToken(String token) throws AccessTokenException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claimsSet = signedJWT.getJWTClaimsSet();

            PublicKeyResponse publicKeyResponse = fetchPublicKeys(claimsSet.getIssuer());
            boolean signatureValid = publicKeyResponse.getKeys().stream().anyMatch(key -> {
                try {
                    return validateJWT(token, key.toString());
                } catch (JOSEException | ParseException e) {
                    log.error("JWT signature validation failed", e);
                    return false;
                }
            });

            if (!signatureValid) {
                log.error("JWT token signature not valid");
                throw new AccessTokenException("JWT token signature not valid");
            }
            if (isTokenExpired(claimsSet)) {
                log.error("JWT token is expired");
                throw new AccessTokenException("JWT token is expired");
            }
            if (!verifyClaimsSet(claimsSet) || !verifyDatasourceClaim(claimsSet)) {
                log.error("Claims validation failed");
                throw new AccessTokenException("Claims validation failed");
            }
            return true;
        } catch (AccessTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token validation failed", e);
            throw new AccessTokenException("Token validation failed", e);
        }
    }

    private boolean isTokenExpired(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        return expirationTime == null || new Date().after(expirationTime);
    }

    /**
     * Returns the issuer's JWKS, fetching it from the Identity Broker on first use and on cache
     * expiry. Falls back to a previously cached entry on HTTP 429 (rate limit).
     */
    private PublicKeyResponse fetchPublicKeys(String issuerUrl) throws AccessTokenException {
        CACHE_LOCK.lock();
        try {
            long currentTime = System.currentTimeMillis();
            PublicKeyResponse cached = issuerUrl != null ? CACHED_PUBLIC_KEY_RESPONSE.get(issuerUrl) : null;
            if (cached != null && currentTime < cached.getExpirationAt()) {
                log.debug("Returning cached public keys for issuer {}", issuerUrl);
                return cached;
            }

            String url = (issuerUrl == null ? (properties.identityBrokerUrl() + "/idb") : issuerUrl)
                    + "/oauth2/v2/keys/verificationjwk";
            HttpURLConnection httpClient = (HttpURLConnection) URI.create(url).toURL().openConnection();
            httpClient.setRequestMethod("GET");

            int responseCode = httpClient.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (InputStream inputStream = httpClient.getInputStream()) {
                    byte[] responseBytes = inputStream.readAllBytes();
                    String response = new String(responseBytes);
                    PublicKeyResponse publicKeyResponse =
                            new ObjectMapper().readValue(response, PublicKeyResponse.class);
                    publicKeyResponse.setExpirationAt(currentTime + cacheDurationMillis);
                    CACHED_PUBLIC_KEY_RESPONSE.put(issuerUrl, publicKeyResponse);
                    log.debug("Fetched and cached public keys for issuer {}", issuerUrl);
                    return publicKeyResponse;
                }
            } else if (responseCode == 429) {
                if (cached != null) {
                    log.warn("JWKS rate limit exceeded for issuer {}, returning stale cached keys", issuerUrl);
                    return cached;
                }
                throw new AccessTokenException("Rate limit exceeded and no cached public keys available");
            } else {
                String errorMessage;
                try (InputStream errorStream = httpClient.getErrorStream()) {
                    errorMessage = errorStream == null ? "" : new String(errorStream.readAllBytes());
                }
                throw new AccessTokenException(
                        "Failed to fetch JWKS, HTTP " + responseCode + ": " + errorMessage);
            }
        } catch (AccessTokenException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error while fetching public keys", e);
            throw new AccessTokenException("Error while fetching public keys", e);
        } finally {
            CACHE_LOCK.unlock();
        }
    }

    private boolean validateJWT(String jwtString, String jwkString) throws JOSEException, ParseException {
        JWK jwk = JWK.parse(jwkString);
        SignedJWT signedJWT = SignedJWT.parse(jwtString);

        if (jwk.getAlgorithm() != null && !jwk.getAlgorithm().equals(signedJWT.getHeader().getAlgorithm())) {
            log.warn("Algorithm mismatch - JWT header: {}, JWKS key: {}",
                    signedJWT.getHeader().getAlgorithm(), jwk.getAlgorithm());
            return false;
        }

        RSAPublicKey publicKey = (RSAPublicKey) jwk.toRSAKey().toPublicKey();
        JWSVerifier verifier = new RSASSAVerifier(publicKey);
        return signedJWT.verify(verifier);
    }

    private boolean verifyClaimsSet(JWTClaimsSet claimsSet) {
        String issuer = claimsSet.getIssuer();
        if (issuer == null || !properties.validIssuers().contains(issuer)) {
            return false;
        }
        return claimsSet.getAudience() != null
                && claimsSet.getSubject() != null
                && claimsSet.getJWTID() != null;
    }

    private boolean verifyDatasourceClaim(JWTClaimsSet claimsSet) throws ParseException {
        return properties.datasourceUrl().equals(claimsSet.getStringClaim(DATASOURCE_URL_CLAIM))
                && properties.datasourceSchemaUuid().equals(claimsSet.getStringClaim(DATASOURCE_SCHEMA_CLAIM));
    }
}
