package com.cisco.wccai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualAgentService {
    private final ObjectMapper objectMapper;

    public void sendMessage(WebSocketSession session, Object message) throws IOException {
        if (Objects.isNull(session) || !session.isOpen()) {
            log.warn("Attempted to send message to closed or null session: {}", session != null ? session.getId() : "null");
            throw new IOException("Attempted to send message to closed or null session");
        }

        try {
            String jsonString = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(jsonString));
        } catch (IOException e) {
            log.error("Failed to send message for sessionId: {}", session.getId(), e);
            throw e;
        }
    }
}
