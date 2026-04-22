package com.cisco.wccai.byova;

import com.cisco.wccai.byova.config.GrpcServerProperties;
import com.cisco.wccai.byova.config.VoiceVaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the BYoVA multi-RPC sample gRPC server.
 *
 * <p>This is intentionally a minimal Spring Boot application: no embedded web server is started;
 * the only listener is the gRPC server defined in {@code com.cisco.wccai.byova.grpc.GrpcServer}.
 */
@SpringBootApplication
@EnableConfigurationProperties({GrpcServerProperties.class, VoiceVaProperties.class})
public class ByovaMultiRpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(ByovaMultiRpcApplication.class, args);
    }
}
