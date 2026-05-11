package com.cisco.wccai.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Lightweight POJO that maps the JWKS response returned by the Cisco Identity Broker
 * ({@code /oauth2/v2/keys/verificationjwk}). Only the fields required to verify an RSA-signed
 * JWS are deserialized.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeyResponse {

    @JsonProperty("keys")
    private List<Key> keys;

    /** When the cached entry expires (epoch millis). Populated by the JWT handler. */
    private Long expirationAt;

    @Getter
    @Setter
    public static class Key {
        @JsonProperty("kty")
        private String kty;

        @JsonProperty("e")
        private String e;

        @JsonProperty("use")
        private String use;

        @JsonProperty("kid")
        private String kid;

        @JsonProperty("n")
        private String n;

        @JsonProperty("alg")
        private String alg;

        @Override
        public String toString() {
            return "{"
                    + "\"kty\":\"" + kty + "\","
                    + "\"e\":\"" + e + "\","
                    + "\"use\":\"" + use + "\","
                    + "\"kid\":\"" + kid + "\","
                    + "\"n\":\"" + n + "\""
                    + (alg != null ? ",\"alg\":\"" + alg + "\"" : "")
                    + "}";
        }
    }
}
