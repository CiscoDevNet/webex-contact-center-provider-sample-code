package com.cisco.wccai.auth;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for inbound JWS/JWT authorization checks performed during the WebSocket
 * handshake.
 *
 * <p>Configured with the {@code auth} prefix in {@code application.properties}. Operators
 * must supply environment-specific values for {@link #identityBrokerUrl()} and
 * {@link #validIssuers()} from the Webex CC documentation for their tenant — no defaults
 * are baked into the source.
 *
 * @param enabled               master switch; when {@code false}, handshakes are accepted without
 *                              validation (intended for local development only)
 * @param identityBrokerUrl     fallback Webex Identity Broker URL used when the inbound JWT does
 *                              not declare an issuer (tenant/region-specific; required when
 *                              {@code enabled} is true)
 * @param validIssuers          allow-list of issuers ({@code iss} claim) accepted by this server
 *                              (tenant/region-specific; required when {@code enabled} is true)
 * @param datasourceUrl         expected value of the {@code com.cisco.datasource.url} JWT claim;
 *                              must match the URL registered with Webex CC (BYoDS)
 * @param datasourceSchemaUuid  expected value of the {@code com.cisco.datasource.schema.uuid}
 *                              claim; this is the BYoVA schema UUID
 * @param publicKeyCacheMinutes how long fetched JWKS responses are cached in memory
 */
@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
        boolean enabled,
        String identityBrokerUrl,
        List<String> validIssuers,
        String datasourceUrl,
        String datasourceSchemaUuid,
        long publicKeyCacheMinutes) {

    public AuthProperties {
        validIssuers = validIssuers == null ? List.of() : List.copyOf(validIssuers);
        if (publicKeyCacheMinutes <= 0) {
            publicKeyCacheMinutes = 60;
        }
        if (enabled) {
            if (identityBrokerUrl == null || identityBrokerUrl.isBlank()) {
                throw new IllegalStateException(
                        "auth.identity-broker-url must be configured when auth.enabled=true; "
                                + "set it to your Webex Identity Broker URL for your tenant/region.");
            }
            if (validIssuers.isEmpty()) {
                throw new IllegalStateException(
                        "auth.valid-issuers must be configured when auth.enabled=true; "
                                + "set it to the issuer(s) used by your Webex tenant/region.");
            }
        }
    }
}
