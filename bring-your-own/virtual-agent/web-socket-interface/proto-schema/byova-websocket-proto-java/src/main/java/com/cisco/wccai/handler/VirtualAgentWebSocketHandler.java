package com.cisco.wccai.handler;

import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.service.AudioStreamingService;
import com.cisco.wccai.service.VirtualAgentProcessor;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;

/**
 * WebSocket handler for the BYoVA {@code /v1/va} endpoint that exchanges protobuf-encoded
 * {@link Voicevirtualagent.VoiceVARequest} / {@link Voicevirtualagent.VoiceVAResponse} messages
 * as binary WebSocket frames.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualAgentWebSocketHandler extends BinaryWebSocketHandler {

    private final VirtualAgentProcessor virtualAgentProcessor;
    private final AudioStreamingService audioStreamingService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Virtual Agent WebSocket connection established. Session ID: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            byte[] payload = extractBytes(message.getPayload());
            Voicevirtualagent.VoiceVARequest voiceVARequest;
            try {
                voiceVARequest = Voicevirtualagent.VoiceVARequest.parseFrom(payload);
            } catch (InvalidProtocolBufferException e) {
                log.error("Failed to parse VoiceVARequest proto from sessionId: {}", session.getId(), e);
                return;
            }

            Voicevirtualagent.VoiceVARequest.VoiceVaInputTypeCase inputTypeCase = voiceVARequest.getVoiceVaInputTypeCase();
            log.info("Received VoiceVARequest ({}) for sessionId: {} and conversationId: {}",
                    inputTypeCase, session.getId(), voiceVARequest.getConversationId());

            virtualAgentProcessor.process(voiceVARequest, session);
        } catch (Exception e) {
            log.error("Error processing voice va request for sessionId: {}", session.getId(), e);
        }
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) {
        log.info("Received pong message from sessionId: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Virtual Agent WebSocket connection closed for sessionId: {}, status: {}", session.getId(), status);
        audioStreamingService.removeSessionState(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Virtual Agent WebSocket transport error for session {}: {}",
                session.getId(), exception.getMessage(), exception);
        audioStreamingService.removeSessionState(session.getId());
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /** Copies the remaining bytes of the supplied buffer into a plain {@code byte[]}. */
    private static byte[] extractBytes(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
