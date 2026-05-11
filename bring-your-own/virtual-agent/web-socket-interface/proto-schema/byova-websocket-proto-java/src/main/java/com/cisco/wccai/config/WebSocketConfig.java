package com.cisco.wccai.config;

import com.cisco.wccai.auth.AuthorizationHandshakeInterceptor;
import com.cisco.wccai.handler.ListVirtualAgentWebSocketHandler;
import com.cisco.wccai.handler.VirtualAgentWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final VirtualAgentWebSocketHandler virtualAgentWebSocketHandler;
    private final ListVirtualAgentWebSocketHandler listVirtualAgentWebSocketHandler;
    private final AuthorizationHandshakeInterceptor authorizationHandshakeInterceptor;

    @Value("${spring.websocket.max-text-message-buffer-size:10485760}")
    private int maxTextMessageBufferSize;

    @Value("${spring.websocket.max-binary-message-buffer-size:10485760}")
    private int maxBinaryMessageBufferSize;

    @Value("${spring.websocket.max-session-idle-timeout:900000}")
    private long maxSessionIdleTimeout;

    public WebSocketConfig(VirtualAgentWebSocketHandler virtualAgentWebSocketHandler,
                           ListVirtualAgentWebSocketHandler listVirtualAgentWebSocketHandler,
                           AuthorizationHandshakeInterceptor authorizationHandshakeInterceptor) {
        this.virtualAgentWebSocketHandler = virtualAgentWebSocketHandler;
        this.listVirtualAgentWebSocketHandler = listVirtualAgentWebSocketHandler;
        this.authorizationHandshakeInterceptor = authorizationHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(virtualAgentWebSocketHandler, "/v1/va")
                .addInterceptors(authorizationHandshakeInterceptor)
                .setAllowedOrigins("*");

        registry.addHandler(listVirtualAgentWebSocketHandler, "/v1/listVirtualAgents")
                .addInterceptors(authorizationHandshakeInterceptor)
                .setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxTextMessageBufferSize);
        container.setMaxBinaryMessageBufferSize(maxBinaryMessageBufferSize);
        container.setMaxSessionIdleTimeout(maxSessionIdleTimeout);
        return container;
    }
}
