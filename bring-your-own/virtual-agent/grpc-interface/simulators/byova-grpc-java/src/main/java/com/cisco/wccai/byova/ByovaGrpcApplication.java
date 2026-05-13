package com.cisco.wccai.byova;

import com.cisco.wccai.byova.config.AuthProperties;
import com.cisco.wccai.byova.config.GrpcServerProperties;
import com.cisco.wccai.byova.config.VoiceVaProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the BYoVA gRPC sample server.
 *
 * <p>This is intentionally a minimal Spring Boot application: no embedded web server is started;
 * the only listener is the gRPC server defined in {@code com.cisco.wccai.byova.grpc.GrpcServer}.
 */
@SpringBootApplication
@EnableConfigurationProperties({GrpcServerProperties.class, VoiceVaProperties.class, AuthProperties.class})
public class ByovaGrpcApplication {

    public static void main(String[] args) {
        SpringApplication.run(ByovaGrpcApplication.class, args);
    }
}
