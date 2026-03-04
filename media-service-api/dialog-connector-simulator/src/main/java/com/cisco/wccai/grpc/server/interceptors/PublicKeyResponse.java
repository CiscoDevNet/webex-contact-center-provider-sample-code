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
    @JsonIgnoreProperties(ignoreUnknown = true)
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
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"kty\":\"").append(kty).append("\",");
            sb.append("\"e\":\"").append(e).append("\",");
            sb.append("\"use\":\"").append(use).append("\",");
            sb.append("\"kid\":\"").append(kid).append("\",");
            sb.append("\"n\":\"").append(n).append("\"");
            if (alg != null) {
                sb.append(",\"alg\":\"").append(alg).append("\"");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
