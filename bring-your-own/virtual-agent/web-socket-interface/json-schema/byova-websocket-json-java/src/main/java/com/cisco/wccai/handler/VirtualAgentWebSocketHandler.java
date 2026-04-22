package com.cisco.wccai.handler;

import com.cisco.wccai.service.VirtualAgentProcessor;
import com.cisco.wccai.ws.voice.VoiceVARequest;
import com.cisco.wccai.ws.voice.WsEnvelopeVoiceVARequest;
import com.cisco.wccai.ws.voice.constant.MessageType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;


@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAgentWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final VirtualAgentProcessor virtualAgentProcessor;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("Virtual Agent WebSocket connection established. Session ID: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            WsEnvelopeVoiceVARequest envelopeVoiceVARequest = objectMapper.readValue(payload, WsEnvelopeVoiceVARequest.class);
            MessageType messageType = MessageType.valueOf(envelopeVoiceVARequest.getType());

            switch (messageType) {
                case VOICE_VA_REQUEST -> {
                    log.info("Received new voice va request: {} for sessionId: {} and conversationId: {}",
                            messageType, session.getId(), envelopeVoiceVARequest.getConversationId());
                    VoiceVARequest voiceVARequest = envelopeVoiceVARequest.getPayload();
                    virtualAgentProcessor.process(voiceVARequest, session);
                }
                case PING -> {
                    log.info("Received ping message from sessionId: {}", session.getId());
                }
                case ERROR -> {
                    log.error("Received error message from sessionId: {}: {}", session.getId(), payload);
                }
                default -> {
                    log.warn("Unsupported message type: {} received for sessionId: {}", messageType, session.getId());
                }
            }
        } catch (Exception e) {
            log.error("Error processing voice va request: {}", message, e);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        log.info("Received pong message: {} from sessionId: {}", message, session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("Virtual Agent WebSocket connection closed for sessionId: {}, status: {}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Virtual Agent WebSocket transport error for session {}: {}", session.getId(), exception.getMessage(), exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }
}
