package com.cisco.wccai.grpc.server.interceptors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PublicKeyResponse {

    @JsonProperty("keys")
    private List<Key> keys;
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
            return "{" +
                    "\"kty\":\"" + kty + "\"," +
                    "\"e\":\"" + e + "\"," +
                    "\"use\":\"" + use + "\"," +
                    "\"kid\":\"" + kid + "\"," +
                    "\"n\":\"" + n + "\"," +
                    "\"alg\":\"" + alg + "\"" +
                    "}";
        }
    }
}
