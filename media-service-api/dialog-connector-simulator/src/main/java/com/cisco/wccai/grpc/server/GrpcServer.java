package com.cisco.wccai.grpc.server;

import com.cisco.wccai.grpc.server.interceptors.AuthorizationServerInterceptor;
import com.cisco.wccai.grpc.server.interceptors.ServiceExceptionHandler;
import com.cisco.wccai.grpc.utils.LoadProperties;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.protobuf.services.ProtoReflectionService;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * The type Grpc server.
 * 
 * This server runs two separate gRPC server instances:
 * 1. Main server (TLS-protected) for audio services on port 8086 (configurable)
 * 2. Health check server (plaintext) on port 8080 (configurable)
 */
public class GrpcServer {

    private static final Logger LOGGER = LoggerFactory.getLogger(GrpcServer.class);
    private static final int PORT = 8086;
    private static final int HEALTH_PORT = 8080;
    private static final Properties properties = LoadProperties.loadProperties();
    @Getter @Setter
    private static boolean serverIsRunning;

    /**
     * The entry point of application.
     *
     * @param args the input arguments
     * @throws IOException          the io exception
     * @throws InterruptedException the interrupted exception
     */
    public static void main(String [] args) throws IOException, InterruptedException {

        // Configure main server port
        String port = System.getenv("PORT");
        int listeningPort;

        if (port == null || port.isEmpty()) {
            String configPort = properties.getProperty("PORT");
            if (configPort != null && !configPort.trim().isEmpty()) {
                listeningPort = Integer.parseInt(configPort.trim());
            } else {
                listeningPort = PORT;
            }
        } else {
            listeningPort = Integer.parseInt(port);
        }

        // Configure health check port
        String healthPortEnv = System.getenv("HEALTH_PORT");
        int healthCheckPort;

        if (healthPortEnv == null || healthPortEnv.isEmpty()) {
            String configHealthPort = properties.getProperty("HEALTH_PORT");
            if (configHealthPort != null && !configHealthPort.trim().isEmpty()) {
                healthCheckPort = Integer.parseInt(configHealthPort.trim());
            } else {
                healthCheckPort = HEALTH_PORT;
            }
        } else {
            healthCheckPort = Integer.parseInt(healthPortEnv);
        }

        LOGGER.info("Main server port: {}, Health check port: {}", listeningPort, healthCheckPort);

        // TLS Configuration
        String tlsCertPath = System.getenv("TLS_CERT_PATH");
        String tlsKeyPath = System.getenv("TLS_KEY_PATH");
        
        if (tlsCertPath == null || tlsCertPath.isEmpty()) {
            tlsCertPath = properties.getProperty("TLS_CERT_PATH");
        }
        if (tlsKeyPath == null || tlsKeyPath.isEmpty()) {
            tlsKeyPath = properties.getProperty("TLS_KEY_PATH");
        }

        // Start health check server (always plaintext, no authentication)
        Server healthServer = ServerBuilder.forPort(healthCheckPort)
                .addService(new com.cisco.wccai.grpc.server.HealthCheckImpl())
                .build()
                .start();
        
        LOGGER.info("✓ Health check server started at port : {} (plaintext, no authentication required)", healthCheckPort);

        Server mainServer;
        
        // Check if TLS is configured for main server
        if (tlsCertPath != null && !tlsCertPath.isEmpty() && 
            tlsKeyPath != null && !tlsKeyPath.isEmpty()) {
            
            LOGGER.info("TLS enabled - Certificate: {}, Key: {}", tlsCertPath, tlsKeyPath);
            
            File certChainFile = new File(tlsCertPath);
            File privateKeyFile = new File(tlsKeyPath);
            
            // Validate certificate files exist
            if (!certChainFile.exists()) {
                throw new IOException("TLS certificate file not found: " + tlsCertPath);
            }
            if (!privateKeyFile.exists()) {
                throw new IOException("TLS private key file not found: " + tlsKeyPath);
            }
            
            // Build SSL context
            SslContext sslContext = GrpcSslContexts.forServer(certChainFile, privateKeyFile)
                    .build();
            
            // Create secure main server with TLS (audio services only, no health check)
            mainServer = NettyServerBuilder.forPort(listeningPort)
                    .sslContext(sslContext)
                    .intercept(new ServiceExceptionHandler())
                    .addService(new VoiceVAImpl())
                    .addService(new ConversationAudioForkServiceImpl())
                    .addService(ProtoReflectionService.newInstance())
                    .intercept(new AuthorizationServerInterceptor())
                    .build()
                    .start();
            
            LOGGER.info("✓ Secure gRPC server started at port : {} with TLS/SSL encryption", listeningPort);
            
        } else {
            // TLS not configured - start without encryption (NOT RECOMMENDED FOR PRODUCTION)
            LOGGER.warn("⚠️  WARNING: TLS is NOT configured! Main server will run WITHOUT encryption.");
            LOGGER.warn("⚠️  This is a SECURITY RISK and should ONLY be used for local development.");
            LOGGER.warn("⚠️  Set TLS_CERT_PATH and TLS_KEY_PATH environment variables or config.properties to enable TLS.");
            
            mainServer = ServerBuilder.forPort(listeningPort)
                    .intercept(new ServiceExceptionHandler())
                    .addService(new VoiceVAImpl())
                    .addService(new ConversationAudioForkServiceImpl())
                    .addService(ProtoReflectionService.newInstance())
                    .intercept(new AuthorizationServerInterceptor())
                    .build()
                    .start();
            
            LOGGER.info("server started at port : {} (UNENCRYPTED)", listeningPort);
        }

        serverIsRunning = true;

        Runtime.getRuntime().addShutdownHook(new Thread( () -> {
            LOGGER.info("Received Shutdown Request");
            mainServer.shutdown();
            healthServer.shutdown();
            LOGGER.info("Successfully stopped both servers");
            serverIsRunning = false;
        }));

        // await for Termination of Program
        mainServer.awaitTermination();
    }
}
