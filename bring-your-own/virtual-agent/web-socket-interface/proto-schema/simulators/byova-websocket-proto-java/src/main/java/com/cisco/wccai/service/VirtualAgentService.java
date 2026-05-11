package com.cisco.wccai.service;

import com.google.protobuf.MessageLite;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Objects;

/**
 * Sends protobuf {@link MessageLite} responses over a WebSocket session as binary frames.
 *
 * <p>Each message is serialized to its compact protobuf wire format and delivered as a single
 * {@link BinaryMessage}. This mirrors the JSON-schema variant
 * ({@code virtual-agent-simulator-ws}), which sends each envelope as a text frame.</p>
 */
@Slf4j
@Service
public class VirtualAgentService {

    public void sendMessage(WebSocketSession session, MessageLite message) throws IOException {
        if (Objects.isNull(session) || !session.isOpen()) {
            log.warn("Attempted to send message to closed or null session: {}",
                    session != null ? session.getId() : "null");
            throw new IOException("Attempted to send message to closed or null session");
        }

        try {
            session.sendMessage(new BinaryMessage(message.toByteArray()));
        } catch (IOException e) {
            log.error("Failed to send protobuf message for sessionId: {}", session.getId(), e);
            throw e;
        }
    }
}
