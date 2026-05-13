package com.cisco.wccai.service;

import com.cisco.wccai.util.AudioFileUtil;
import com.cisco.wccai.ws.voice.DTMFInputsWrapper;
import com.cisco.wccai.ws.voice.VoiceVARequest;
import com.cisco.wccai.ws.voice.constant.DTMFDigits;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static com.cisco.wccai.common.AudioConstant.*;
import static com.cisco.wccai.ws.voice.constant.VoiceVAInputMode.INPUT_VOICE_DTMF;


@Slf4j
@Service
@RequiredArgsConstructor
public class DtmfService {
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;
    private boolean isCallEndInputReceived = false;
    private boolean isCallTransferInputReceived = false;

    public void processDtmfInput(VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        String conversationId = voiceVARequest.getConversationId();
        log.info("Received DTMF input for conversationId: {}", conversationId);
        DTMFInputsWrapper dtmfInputsWrapper = (DTMFInputsWrapper) voiceVARequest.getVoiceVaInputType();
        List<DTMFDigits> dtmfDigits = dtmfInputsWrapper.getDtmfInput().getDtmfEvents();

        if (dtmfDigits.isEmpty()) {
            log.info("Received empty DTMF input for conversationId: {}", conversationId);
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.noInputEventResponse());
        }

        // reset flags
        isCallEndInputReceived = false;
        isCallTransferInputReceived = false;

        // send audio bytes and events based on the DTMF input
        mapDtmfInputToEvents(conversationId, dtmfDigits, session);

        if (isCallEndInputReceived) {
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.callEndEvent());
        } else if (isCallTransferInputReceived) {
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.agentTransferEvent());
        }
    }

    public void mapDtmfInputToEvents(String conversationId, List<DTMFDigits> dtmfDigits, WebSocketSession session) throws IOException {
        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(YOU_PRESSED), INPUT_VOICE_DTMF));
        for (DTMFDigits dtmfDigit : dtmfDigits) {
            switch (dtmfDigit) {
                case DTMF_DIGIT_ONE ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(ONE_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_TWO ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(TWO_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_THREE ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(THREE_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_FOUR ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(FOUR_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_FIVE -> {
                    virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(FIVE_AUDIO), INPUT_VOICE_DTMF));
                    isCallTransferInputReceived = true;
                }
                case DTMF_DIGIT_SIX ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(SIX_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_SEVEN ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(SEVEN_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_EIGHT ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(EIGHT_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_NINE ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(NINE_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_ZERO ->
                        virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(ZERO_AUDIO), INPUT_VOICE_DTMF));
                case DTMF_DIGIT_STAR -> {
                    virtualAgentService.sendMessage(session, virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(STAR_AUDIO), INPUT_VOICE_DTMF));
                    isCallEndInputReceived = true;
                }
                default ->
                        log.info("Received unknown DTMF digit: {} for conversationId: {}", dtmfDigit, conversationId);
            }
        }
    }

}
