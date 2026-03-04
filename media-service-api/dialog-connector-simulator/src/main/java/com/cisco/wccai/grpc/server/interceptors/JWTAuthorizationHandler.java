package com.cisco.wccai.grpc.server.interceptors;

import com.cisco.wccai.grpc.utils.LoadProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class JWTAuthorizationHandler implements AuthorizationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(JWTAuthorizationHandler.class);
    private static final Properties PROPERTIES = LoadProperties.loadProperties();

    private static final String IDENTITY_BROKER_URL = "https://idbrokerbts.webex.com";
    private final String VALID_DATASOURCE_URL = System.getenv("DATASOURCE_URL") != null ? 
        System.getenv("DATASOURCE_URL") : 
        PROPERTIES.getProperty("DATASOURCE_URL", "https://dialog-connector-simulator.intgus1.ciscoccservice.com:443");
    private static final String DATASOURCE_URL_KEY = "com.cisco.datasource.url";
    private static final String DATASOURCE_SCHEMA_KEY = "com.cisco.datasource.schema.uuid";
    private static final String VALID_DATASOURCE_SCHEMA_UUID = "523e1b7f-4693-47bc-b84e-a7b7a505fb0b";
    private static final List<String> LIST_VALID_ISSUERS = List.of("https://idbrokerbts.webex.com/idb", "https://idbrokerbts-eu.webex.com/idb", "https://idbroker.webex.com/idb", "https://idbroker-eu.webex.com/idb", "https://idbroker-b-us.webex.com/idb", "https://idbroker-ca.webex.com/idb");;

    private final static HashMap<String, PublicKeyResponse> cachedPublicKeyResponse = new HashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(60); // Cache duration of 60 minutes
    private static final ReentrantLock cacheLock = new ReentrantLock();
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 1000; // 1 second

    public JWTAuthorizationHandler() {
        LOGGER.info("JWTAuthorizationHandler initialized with datasource URL: {}", VALID_DATASOURCE_URL);
    }

    /*
    This method validates the JWS/JWT token received as part of the data source registration response. and used while sending data over gRPC.
    In this method, we fetch Cisco's public key and validates the signature of the JWS/JWT using this public key.
    This validates that data over gRPC is coming from Cisco and is not tampered.
    */
    @Override
    public boolean validateToken(String token) throws AccessTokenException {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);
            var tokenWithClaimsSet = signedJWT.getJWTClaimsSet();
            PublicKeyResponse publicKeyResponse = fetchPublicKeys(tokenWithClaimsSet.getIssuer());
            boolean isJWTTokenSignatureValid = publicKeyResponse.getKeys().stream()
                    .anyMatch(key -> {
                        try {
                            return validateJWT(token, key.toString());
                        } catch (JOSEException | ParseException e) {
                            LOGGER.error("JWT signature validation failed", e);
                            return false;
                        }
                    });
            if (isJWTTokenSignatureValid) {
                if (isTokenExpired(tokenWithClaimsSet)) {
                    LOGGER.error("JWT token is expired");
                    throw new AccessTokenException("JWT token is expired");
                }
                boolean areClaimsValid = verifyClaimsSet(tokenWithClaimsSet);
                boolean areDataSourceClaimValid = verifyDatasourceClaim(tokenWithClaimsSet);
                if (!(areClaimsValid && areDataSourceClaimValid)) {
                    LOGGER.error("Claims validation failed");
                    throw new AccessTokenException("Claims validation failed");
                }
                return true;
            }
            LOGGER.error("JWT token signature not valid");
            throw new AccessTokenException("JWT token signature not valid");
        } catch (Exception e) {
            LOGGER.error("Token validation failed", e);
            throw new AccessTokenException("Token validation failed", e);
        }
    }

    private boolean isTokenExpired(JWTClaimsSet claimsSet) {
        Date expirationTime = claimsSet.getExpirationTime();
        return expirationTime == null || new Date().after(expirationTime);
    }

    private PublicKeyResponse fetchPublicKeys(String issuerUrl) throws AccessTokenException {
        cacheLock.lock();
        try {
            long currentTime = System.currentTimeMillis();
            if (issuerUrl != null && cachedPublicKeyResponse.getOrDefault(issuerUrl , null) != null && currentTime < cachedPublicKeyResponse.get(issuerUrl).getExpirationAt()) {
                LOGGER.info("Returning cached public keys");
                return cachedPublicKeyResponse.get(issuerUrl);
            }

            String url = (issuerUrl == null ? (IDENTITY_BROKER_URL + "/idb") : issuerUrl) + "/oauth2/v2/keys/verificationjwk";
            
            // Retry logic with Retry-After header support and exponential backoff
            int attempt = 0;
            long retryDelay = INITIAL_RETRY_DELAY_MS;
            
            while (attempt < MAX_RETRY_ATTEMPTS) {
                HttpURLConnection httpClient = (HttpURLConnection) new URL(url).openConnection();
                httpClient.setRequestMethod("GET");

                int responseCode = httpClient.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    LOGGER.info("Public keys fetched successfully on attempt {}", attempt + 1);
                    try (InputStream inputStream = httpClient.getInputStream()) {
                        byte[] responseBytes = inputStream.readAllBytes();
                        String response = new String(responseBytes);
                        ObjectMapper objectMapper = new ObjectMapper();
                        var publicKeyResponse = objectMapper.readValue(response, PublicKeyResponse.class);
                        
                        // Parse Cache-Control header for cache duration
                        String cacheControl = httpClient.getHeaderField("Cache-Control");
                        long cacheDuration = parseCacheDuration(cacheControl);
                        publicKeyResponse.setExpirationAt(currentTime + cacheDuration);
                        
                        cachedPublicKeyResponse.put(issuerUrl, publicKeyResponse);
                        return publicKeyResponse;
                    }
                } else if (responseCode == 429) {
                    attempt++;
                    
                    if (attempt >= MAX_RETRY_ATTEMPTS) {
                        // Max retries reached, return cached keys if available
                        if (cachedPublicKeyResponse.getOrDefault(issuerUrl, null) != null) {
                            LOGGER.info("Max retry attempts reached. Returning cached public keys");
                            return cachedPublicKeyResponse.get(issuerUrl);
                        } else {
                            throw new AccessTokenException("Rate limit exceeded after " + MAX_RETRY_ATTEMPTS + " attempts and no cached public keys available");
                        }
                    }
                    
                    // Check for Retry-After header (priority over exponential backoff)
                    String retryAfterHeader = httpClient.getHeaderField("Retry-After");
                    long waitTimeMs;
                    
                    if (retryAfterHeader != null && !retryAfterHeader.isEmpty()) {
                        // Retry-After can be in seconds (integer) or HTTP date format
                        // We'll handle the seconds format (most common for 429)
                        try {
                            long retryAfterSeconds = Long.parseLong(retryAfterHeader);
                            waitTimeMs = TimeUnit.SECONDS.toMillis(retryAfterSeconds);
                            LOGGER.warn("Rate limit (429) received on attempt {}. Retry-After header specifies {} seconds ({} ms)", 
                                       attempt, retryAfterSeconds, waitTimeMs);
                        } catch (NumberFormatException e) {
                            // If not a number, fall back to exponential backoff
                            LOGGER.warn("Rate limit (429) received on attempt {}. Retry-After header not parseable, using exponential backoff: {} ms", 
                                       attempt, retryDelay);
                            waitTimeMs = retryDelay;
                        }
                    } else {
                        // No Retry-After header, use exponential backoff
                        LOGGER.warn("Rate limit (429) received on attempt {}. No Retry-After header, using exponential backoff: {} ms", 
                                   attempt, retryDelay);
                        waitTimeMs = retryDelay;
                    }
                    
                    // Wait before retrying
                    try {
                        Thread.sleep(waitTimeMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AccessTokenException("Retry interrupted", ie);
                    }
                    
                    // Update exponential backoff delay for next iteration (only used if no Retry-After header)
                    retryDelay *= 2;
                    
                } else {
                    try (InputStream errorStream = httpClient.getErrorStream()) {
                        byte[] errorBytes = errorStream.readAllBytes();
                        String errorMessage = new String(errorBytes);
                        throw new RuntimeException("Failed : HTTP error code : " + responseCode + " and error message: " + errorMessage);
                    }
                }
            }
            
            // Should not reach here, but handle gracefully
            throw new AccessTokenException("Failed to fetch public keys after retries");
            
        } catch (Exception e) {
            LOGGER.error("Error while fetching public keys", e);
            throw new AccessTokenException("Error while fetching public keys", e);
        } finally {
            cacheLock.unlock();
        }
    }

    /**
     * Parses the Cache-Control header to extract cache duration.
     * Looks for max-age directive and converts to milliseconds.
     * Falls back to default CACHE_DURATION if header is missing or invalid.
     * 
     * @param cacheControl The Cache-Control header value
     * @return Cache duration in milliseconds
     */
    private long parseCacheDuration(String cacheControl) {
        if (cacheControl == null || cacheControl.isEmpty()) {
            LOGGER.debug("No Cache-Control header found, using default cache duration: {} minutes", TimeUnit.MILLISECONDS.toMinutes(CACHE_DURATION));
            return CACHE_DURATION;
        }
        
        try {
            // Parse Cache-Control header for max-age directive
            // Example: "public, max-age=3600" or "max-age=3600, public"
            String[] directives = cacheControl.split(",");
            for (String directive : directives) {
                String trimmed = directive.trim();
                if (trimmed.startsWith("max-age=")) {
                    String maxAgeValue = trimmed.substring("max-age=".length()).trim();
                    long maxAgeSeconds = Long.parseLong(maxAgeValue);
                    long cacheDurationMs = TimeUnit.SECONDS.toMillis(maxAgeSeconds);
                    LOGGER.info("Parsed Cache-Control max-age: {} seconds ({} minutes)", maxAgeSeconds, TimeUnit.SECONDS.toMinutes(maxAgeSeconds));
                    return cacheDurationMs;
                }
            }
            LOGGER.debug("No max-age directive found in Cache-Control header, using default cache duration");
            return CACHE_DURATION;
        } catch (NumberFormatException e) {
            LOGGER.warn("Failed to parse max-age from Cache-Control header: {}. Using default cache duration", cacheControl, e);
            return CACHE_DURATION;
        }
    }

    private boolean validateJWT(String jwtString, String jwkString) throws JOSEException, ParseException {
        JWK jwk = JWK.parse(jwkString);
        RSAPublicKey publicKey = (RSAPublicKey) jwk.toRSAKey().toPublicKey();
        SignedJWT signedJWT = SignedJWT.parse(jwtString);
        JWSVerifier verifier = new RSASSAVerifier(publicKey);
        boolean verified = signedJWT.verify(verifier);
        if (verified) {
            LOGGER.info("JWT is valid!");
            return true;
        } else {
            LOGGER.info("JWT is invalid!");
            return false;
        }
    }

    private boolean verifyClaimsSet(JWTClaimsSet claimsSet) {
        String issuer = claimsSet.getIssuer();
        if (issuer == null || !LIST_VALID_ISSUERS.contains(issuer))
                return false;
            return claimsSet.getAudience() != null &&
                    claimsSet.getSubject() != null &&
                    claimsSet.getJWTID() != null;
        }

        private boolean verifyDatasourceClaim (JWTClaimsSet tokenWithClaimsSet) throws ParseException {
            return VALID_DATASOURCE_URL.equals(tokenWithClaimsSet.getStringClaim(DATASOURCE_URL_KEY)) &&
                    VALID_DATASOURCE_SCHEMA_UUID.equals(tokenWithClaimsSet.getStringClaim(DATASOURCE_SCHEMA_KEY));
        }
}
