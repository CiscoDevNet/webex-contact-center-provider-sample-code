package com.cisco.wccai.config;

import com.cisco.wccai.handler.ListVirtualAgentWebSocketHandler;
import com.cisco.wccai.handler.VirtualAgentWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;


@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final VirtualAgentWebSocketHandler virtualAgentWebSocketHandler;
    private final ListVirtualAgentWebSocketHandler listVirtualAgentWebSocketHandler;

    @Value("${spring.websocket.max-text-message-buffer-size:10485760}")
    private int maxTextMessageBufferSize;

    @Value("${spring.websocket.max-binary-message-buffer-size:10485760}")
    private int maxBinaryMessageBufferSize;

    @Value("${spring.websocket.max-session-idle-timeout:900000}")
    private long maxSessionIdleTimeout;

    public WebSocketConfig(VirtualAgentWebSocketHandler virtualAgentWebSocketHandler,
                           ListVirtualAgentWebSocketHandler listVirtualAgentWebSocketHandler) {
        this.virtualAgentWebSocketHandler = virtualAgentWebSocketHandler;
        this.listVirtualAgentWebSocketHandler = listVirtualAgentWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(virtualAgentWebSocketHandler, "/v1/va")
                .setAllowedOrigins("*");

        registry.addHandler(listVirtualAgentWebSocketHandler, "/v1/listVirtualAgents")
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        // Set max text message buffer size (default: 10MB)
        container.setMaxTextMessageBufferSize(maxTextMessageBufferSize);
        // Set max binary message buffer size (default: 10MB)
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageBufferSize);
        // Set max session idle timeout (default: 15 minutes)
        container.setMaxSessionIdleTimeout(maxSessionIdleTimeout);
        return container;
    }
}

