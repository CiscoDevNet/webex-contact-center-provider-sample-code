package com.cisco.wccai.forking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * gRPC server-level configuration.
 *
 * <p>Configured with the {@code grpc.server} prefix in {@code application.yml}.
 */
@ConfigurationProperties(prefix = "grpc.server")
public record GrpcServerProperties(int port, int shutdownTimeoutSeconds) {

    public GrpcServerProperties {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("grpc.server.port must be between 1 and 65535");
        }
        if (shutdownTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("grpc.server.shutdown-timeout-seconds must be positive");
        }
    }
}
