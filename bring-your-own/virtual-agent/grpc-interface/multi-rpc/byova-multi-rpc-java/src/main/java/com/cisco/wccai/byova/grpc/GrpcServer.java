package com.cisco.wccai.byova.grpc;

import com.cisco.wccai.byova.config.GrpcServerProperties;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts a Netty-based gRPC server once Spring reports the application is ready and stops it
 * gracefully on container shutdown.
 */
@Slf4j
@Component
public class GrpcServer {

    private final GrpcServerProperties properties;
    private final List<BindableService> services;
    private final MetadataInterceptor metadataInterceptor;
    private final AuthorizationServerInterceptor authorizationServerInterceptor;

    private Server server;

    public GrpcServer(
            GrpcServerProperties properties,
            List<BindableService> services,
            MetadataInterceptor metadataInterceptor,
            AuthorizationServerInterceptor authorizationServerInterceptor) {
        this.properties = properties;
        this.services = List.copyOf(services);
        this.metadataInterceptor = metadataInterceptor;
        this.authorizationServerInterceptor = authorizationServerInterceptor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (server != null) {
            return;
        }
        NettyServerBuilder builder = NettyServerBuilder.forPort(properties.port());
        services.forEach(builder::addService);
        // Order matters: the last-added interceptor runs first. Authorize the call before
        // we look at any other metadata.
        builder.intercept(metadataInterceptor);
        builder.intercept(authorizationServerInterceptor);

        try {
            server = builder.build().start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Unable to start gRPC server on port " + properties.port(), e);
        }
        log.info("gRPC server started on port {} with {} service(s)", properties.port(), services.size());
    }

    @PreDestroy
    public void stop() {
        if (server == null) {
            return;
        }
        log.info("Shutting down gRPC server...");
        try {
            server.shutdown();
            if (!server.awaitTermination(properties.shutdownTimeoutSeconds(), TimeUnit.SECONDS)) {
                log.warn("gRPC server did not terminate gracefully within {}s; forcing shutdown",
                        properties.shutdownTimeoutSeconds());
                server.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            server.shutdownNow();
            log.error("Interrupted while stopping gRPC server", e);
        }
    }
}
