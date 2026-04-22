package com.cisco.wccai.service;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.google.protobuf.Value;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Dispatches a decoded {@link Voicevirtualagent.VoiceVARequest} protobuf message to the
 * appropriate service based on its {@code voice_va_input_type} oneof case.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualAgentProcessor {
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;
    private final AudioStreamingService audioStreamingService;
    private final DtmfService dtmfService;

    public void process(Voicevirtualagent.VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        String conversationId = voiceVARequest.getConversationId();
        Voicevirtualagent.VoiceVARequest.VoiceVaInputTypeCase inputTypeCase = voiceVARequest.getVoiceVaInputTypeCase();

        switch (inputTypeCase) {
            case AUDIO_INPUT -> audioStreamingService.processAudioStream(voiceVARequest, session);
            case EVENT_INPUT -> processEventInput(voiceVARequest, session);
            case DTMF_INPUT -> dtmfService.processDtmfInput(voiceVARequest, session);
            default ->
                    log.warn("Received unknown ({}) Voice VA input type for conversationId: {}", inputTypeCase, conversationId);
        }
    }

    private void processEventInput(Voicevirtualagent.VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        ByovaCommon.EventInput.EventType eventType = voiceVARequest.getEventInput().getEventType();
        log.info("Received {} event for conversationId: {}", eventType, voiceVARequest.getConversationId());

        switch (eventType) {
            case SESSION_START -> virtualAgentService.sendMessage(session, virtualAgentAdaptor.callStartEventResponse());
            case SESSION_END -> log.info("Session is ended for conversationId: {}", voiceVARequest.getConversationId());
            case NO_INPUT -> virtualAgentService.sendMessage(session, virtualAgentAdaptor.noInputEventResponse());
            case CUSTOM_EVENT -> handleCustomEvent(voiceVARequest, session);
            default -> log.warn("Ignoring event type: {} for conversationId: {}", eventType, voiceVARequest.getConversationId());
        }
    }

    private void handleCustomEvent(Voicevirtualagent.VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        ByovaCommon.EventInput eventInput = voiceVARequest.getEventInput();
        String customEventType = eventInput.getEventType().name();
        String customEventName = eventInput.getName();
        log.info("Processing {} event with name: {} for conversationId: {}",
                customEventType, customEventName, voiceVARequest.getConversationId());
        Map<String, Value> fields = eventInput.getParameters().getFieldsMap();
        for (Map.Entry<String, Value> entry : fields.entrySet()) {
            log.info("Custom event parameter: {} = {}", entry.getKey(), entry.getValue());
        }
        virtualAgentService.sendMessage(session, virtualAgentAdaptor.callStartEventResponse());
    }

    public void sendVirtualAgentsList(WebSocketSession session) throws IOException {
        List<ByovaCommon.VirtualAgentInfo> vaList = List.of(
                ByovaCommon.VirtualAgentInfo.newBuilder()
                        .setVirtualAgentId("1")
                        .setVirtualAgentName("Karen")
                        .putAttributes("description", "Scripted Virtual Agent")
                        .build(),
                ByovaCommon.VirtualAgentInfo.newBuilder()
                        .setVirtualAgentId("2")
                        .setVirtualAgentName("Veronika")
                        .putAttributes("description", "Autonomous Virtual Agent")
                        .build(),
                ByovaCommon.VirtualAgentInfo.newBuilder()
                        .setVirtualAgentId("3")
                        .setVirtualAgentName("Edith")
                        .putAttributes("description", "Smart Virtual Assistant")
                        .build());

        ByovaCommon.ListVAResponse listVAResponse = ByovaCommon.ListVAResponse.newBuilder()
                .addAllVirtualAgents(vaList)
                .build();
        virtualAgentService.sendMessage(session, listVAResponse);
        log.info("Sent virtual agent list to sessionId: {}", session.getId());
    }
}
