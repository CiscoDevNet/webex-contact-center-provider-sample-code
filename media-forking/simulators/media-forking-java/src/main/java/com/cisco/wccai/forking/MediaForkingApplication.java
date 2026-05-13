package com.cisco.wccai.forking;

import com.cisco.wccai.forking.config.AuthProperties;
import com.cisco.wccai.forking.config.ForkingProperties;
import com.cisco.wccai.forking.config.GrpcServerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Conversation Audio Forking sample gRPC server.
 *
 * <p>This is intentionally a minimal Spring Boot application: no embedded web server is started;
 * the only listener is the gRPC server defined in {@code com.cisco.wccai.forking.grpc.GrpcServer}.
 */
@SpringBootApplication
@EnableConfigurationProperties({GrpcServerProperties.class, ForkingProperties.class, AuthProperties.class})
public class MediaForkingApplication {

    public static void main(String[] args) {
        SpringApplication.run(MediaForkingApplication.class, args);
    }
}
