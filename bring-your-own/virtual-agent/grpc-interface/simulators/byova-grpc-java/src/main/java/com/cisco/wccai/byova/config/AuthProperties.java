package com.cisco.wccai.byova.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for inbound JWS/JWT authorization checks performed on every gRPC call.
 *
 * <p>Configured with the {@code auth} prefix in {@code application.yml}.
 *
 * @param enabled            master switch; when {@code false}, requests are accepted without
 *                           validation (intended for local development only)
 * @param identityBrokerUrl  fallback Cisco Identity Broker URL used when the inbound JWT does
 *                           not declare an issuer
 * @param validIssuers       list of issuers (`iss` claim) accepted by this server
 * @param datasourceUrl      expected value of the {@code com.cisco.datasource.url} JWT claim;
 *                           must match the URL registered with Webex CC (BYoDS)
 * @param datasourceSchemaUuid expected value of the {@code com.cisco.datasource.schema.uuid}
 *                             claim; this is the BYoVA schema UUID
 * @param publicKeyCacheMinutes  how long fetched JWKS responses are cached in memory
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
        if (identityBrokerUrl == null || identityBrokerUrl.isBlank()) {
            identityBrokerUrl = "https://idbrokerbts.webex.com";
        }
        if (validIssuers == null || validIssuers.isEmpty()) {
            validIssuers = List.of(
                    "https://idbrokerbts.webex.com/idb",
                    "https://idbrokerbts-eu.webex.com/idb",
                    "https://idbroker.webex.com/idb",
                    "https://idbroker-eu.webex.com/idb",
                    "https://idbroker-b-us.webex.com/idb",
                    "https://idbroker-ca.webex.com/idb");
        } else {
            validIssuers = List.copyOf(validIssuers);
        }
        if (publicKeyCacheMinutes <= 0) {
            publicKeyCacheMinutes = 60;
        }
    }
}
