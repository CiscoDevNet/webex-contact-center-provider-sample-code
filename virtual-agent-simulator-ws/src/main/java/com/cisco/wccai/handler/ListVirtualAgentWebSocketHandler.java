package com.cisco.wccai.handler;

import com.cisco.wccai.service.VirtualAgentProcessor;
import com.cisco.wccai.ws.list.ListVARequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Slf4j
@Component
@RequiredArgsConstructor
public class ListVirtualAgentWebSocketHandler extends TextWebSocketHandler {
    private final Map<String, WebSocketSession> listVASessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final VirtualAgentProcessor virtualAgentProcessor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        listVASessions.put(session.getId(), session);
        log.info("List Virtual Agent WebSocket connection established with sessionId: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            log.info("Received message from sessionId: {}, payload: {}", session.getId(), payload);

            ListVARequest listVARequest = objectMapper.readValue(payload, ListVARequest.class);
            log.info("ListVARequest payload: {}, orgId: {}", listVARequest, listVARequest.getCustomerOrgId());
            virtualAgentProcessor.sendVirtualAgentsList(session);
        } catch (Exception e) {
            log.error("Failed to parse ListVARequest from payload", e);
            throw e;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        listVASessions.remove(session.getId());
        log.info("List Virtual Agent WebSocket connection closed for sessionId: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("List Virtual Agent WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
        listVASessions.remove(session.getId());
    }

    public int getActiveSessionCount() {
        return listVASessions.size();
    }
}
