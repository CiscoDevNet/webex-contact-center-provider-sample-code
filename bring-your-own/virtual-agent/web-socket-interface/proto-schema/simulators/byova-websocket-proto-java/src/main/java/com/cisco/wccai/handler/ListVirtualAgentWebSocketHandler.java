package com.cisco.wccai.handler;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wccai.service.VirtualAgentProcessor;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket handler for the BYoVA {@code /v1/listVirtualAgents} endpoint. It accepts a
 * {@link ByovaCommon.ListVARequest} as a binary protobuf message and responds with a
 * {@link ByovaCommon.ListVAResponse} describing the configured sample virtual agents.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListVirtualAgentWebSocketHandler extends BinaryWebSocketHandler {
    private final Map<String, WebSocketSession> listVASessions = new ConcurrentHashMap<>();
    private final VirtualAgentProcessor virtualAgentProcessor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        listVASessions.put(session.getId(), session);
        log.info("List Virtual Agent WebSocket connection established with sessionId: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            byte[] payload = extractBytes(message.getPayload());
            ByovaCommon.ListVARequest listVARequest;
            try {
                listVARequest = ByovaCommon.ListVARequest.parseFrom(payload);
            } catch (InvalidProtocolBufferException e) {
                log.error("Failed to parse ListVARequest proto from sessionId: {}", session.getId(), e);
                return;
            }
            log.info("Received ListVARequest for sessionId: {}, orgId: {}, defaultVaEnabled: {}",
                    session.getId(), listVARequest.getCustomerOrgId(), listVARequest.getIsDefaultVirtualAgentEnabled());
            virtualAgentProcessor.sendVirtualAgentsList(session);
        } catch (Exception e) {
            log.error("Failed to parse ListVARequest from payload for sessionId: {}", session.getId(), e);
            throw e;
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        listVASessions.remove(session.getId());
        log.info("List Virtual Agent WebSocket connection closed for sessionId: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("List Virtual Agent WebSocket transport error for session {}: {}",
                session.getId(), exception.getMessage(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
        listVASessions.remove(session.getId());
    }

    public int getActiveSessionCount() {
        return listVASessions.size();
    }

    private static byte[] extractBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
