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

    private final String VALID_DATASOURCE_URL = PROPERTIES.getProperty("DATASOURCE_URL", "https://dialog-connector-simulator.intgus1.ciscoccservice.com:443");
    private static final String DATASOURCE_URL_KEY = "com.cisco.datasource.url";
    private static final String DATASOURCE_SCHEMA_KEY = "com.cisco.datasource.schema.uuid";
    private static final String VALID_DATASOURCE_SCHEMA_UUID = "5397013b-7920-4ffc-807c-e8a3e0a18f43";
    private static final List<String> LIST_VALID_ISSUERS = List.of("https://idbrokerbts.webex.com/idb", "https://idbrokerbts-eu.webex.com/idb", "https://idbroker.webex.com/idb", "https://idbroker-eu.webex.com/idb", "https://idbroker-b-us.webex.com/idb", "https://idbroker-ca.webex.com/idb");;

    private final static HashMap<String, PublicKeyResponse> cachedPublicKeyResponse = new HashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(60); // Cache duration of 60 minutes
    private static final ReentrantLock cacheLock = new ReentrantLock();

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
            
            // SECURITY: Validate issuer BEFORE fetching keys to prevent SSRF attacks
            String issuer = tokenWithClaimsSet.getIssuer();
            if (issuer == null || issuer.isEmpty()) {
                LOGGER.error("JWT token missing 'iss' claim");
                throw new AccessTokenException("JWT token missing 'iss' claim");
            }
            
            if (!LIST_VALID_ISSUERS.contains(issuer)) {
                LOGGER.error("Invalid issuer: {}. Must be one of: {}", issuer, LIST_VALID_ISSUERS);
                throw new AccessTokenException("Invalid issuer: " + issuer);
            }
            
            PublicKeyResponse publicKeyResponse = fetchPublicKeys(issuer);
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
        } catch (AccessTokenException e) {
            // Re-throw AccessTokenException to preserve the specific error message
            throw e;
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

            // issuerUrl is guaranteed to be non-null and validated at this point
            String url = issuerUrl + "/oauth2/v2/keys/verificationjwk";
            HttpURLConnection httpClient = (HttpURLConnection) new URL(url).openConnection();
            httpClient.setRequestMethod("GET");

            int responseCode = httpClient.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                LOGGER.info("Public keys fetched successfully");
                try (InputStream inputStream = httpClient.getInputStream()) {
                    byte[] responseBytes = inputStream.readAllBytes();
                    String response = new String(responseBytes);
                    ObjectMapper objectMapper = new ObjectMapper();
                    var publicKeyResponse = objectMapper.readValue(response, PublicKeyResponse.class);
                    publicKeyResponse.setExpirationAt(currentTime + CACHE_DURATION);
                    cachedPublicKeyResponse.put(issuerUrl, publicKeyResponse);
                    return publicKeyResponse;
                }
            } else if (responseCode == 429) {
                if (cachedPublicKeyResponse.getOrDefault(issuerUrl, null) != null) {
                    LOGGER.info("Rate limit exceeded, returning cached public keys");
                    return cachedPublicKeyResponse.get(issuerUrl);
                } else {
                    throw new AccessTokenException("Rate limit exceeded and no cached public keys available");
                }
            } else {
                try (InputStream errorStream = httpClient.getErrorStream()) {
                    byte[] errorBytes = errorStream.readAllBytes();
                    String errorMessage = new String(errorBytes);
                    throw new RuntimeException("Failed : HTTP error code : " + responseCode + " and error message: " + errorMessage);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error while fetching public keys", e);
            throw new AccessTokenException("Error while fetching public keys");
        } finally {
            cacheLock.unlock();
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
