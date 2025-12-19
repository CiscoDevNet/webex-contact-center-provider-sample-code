package com.cisco.wccai.service;

import com.cisco.wccai.ws.list.ListVAResponse;
import com.cisco.wccai.ws.list.VirtualAgentInfo;
import com.cisco.wccai.ws.voice.*;
import com.cisco.wccai.ws.voice.constant.EventInputType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualAgentProcessor {
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;
    private final AudioStreamingService audioStreamingService;
    private final DtmfService dtmfService;

    public void process(VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        String conversationId = voiceVARequest.getConversationId();
        VoiceVAInputType voiceVAInputType = voiceVARequest.getVoiceVaInputType();

        switch (voiceVAInputType) {
            case VoiceInputWrapper voiceInputWrapper -> audioStreamingService.processAudioStream(voiceInputWrapper, session);
            case EventInputWrapper eventInputWrapper -> processEventInput(voiceVARequest, eventInputWrapper, session);
            case DTMFInputsWrapper dtmfWrapper -> dtmfService.processDtmfInput(voiceVARequest, session);
            default -> log.warn("Received unknown({}) Voice VA input type for conversationId: {}", voiceVAInputType.getClass().getSimpleName(), conversationId);
        }
    }

    private void processEventInput(VoiceVARequest voiceVARequest, EventInputWrapper eventInputWrapper, WebSocketSession session) throws IOException {
        EventInputType eventInputType = eventInputWrapper.getEventInput().getEventType();
        log.info("Received {} event for conversationId: {}", eventInputType, voiceVARequest.getConversationId());

        switch (eventInputType) {
            case SESSION_START -> virtualAgentService.sendMessage(session, virtualAgentAdaptor.callStartEventResponse());
            case SESSION_END -> log.info("Session is ended for conversationId: {}", voiceVARequest.getConversationId());
            case NO_INPUT -> virtualAgentService.sendMessage(session, virtualAgentAdaptor.noInputEventResponse());
            case CUSTOM_EVENT -> handleCustomEvent(voiceVARequest, eventInputWrapper, session);
            default -> log.warn("Ignoring event type: {} for conversationId: {}", eventInputType, voiceVARequest.getConversationId());
        }
    }

    private void handleCustomEvent(VoiceVARequest voiceVARequest, EventInputWrapper eventInputWrapper, WebSocketSession session) throws IOException {
        String customEventType = eventInputWrapper.getEventInput().getEventType().name();
        String customEventName = eventInputWrapper.getEventInput().getName();
        log.info("Processing {} event with name: {} for conversationId: {}", customEventType, customEventName, voiceVARequest.getConversationId());
        Map<String, Object> objectMap = eventInputWrapper.getEventInput().getParameters();
        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
            log.info("Custom event parameter: {} = {}", entry.getKey(), entry.getValue());
        }
        virtualAgentService.sendMessage(session, virtualAgentAdaptor.callStartEventResponse());
    }

    public void sendVirtualAgentsList(WebSocketSession session) throws IOException {
        List<VirtualAgentInfo> vaList = List.of(
                VirtualAgentInfo.builder().id("1").name("Karen").description("Scripted Virtual Agent").build(),
                VirtualAgentInfo.builder().id("2").name("Veronika").description("Autonomous Virtual Agent").build(),
                VirtualAgentInfo.builder().id("3").name("Edith").description("Smart Virtual Assistant").build());

        ListVAResponse listVAResponse = ListVAResponse.builder()
                .virtualAgents(vaList)
                .build();
        virtualAgentService.sendMessage(session, listVAResponse);
        log.info("Sent virtual agent list to sessionId: {}", session.getId());
    }
}
