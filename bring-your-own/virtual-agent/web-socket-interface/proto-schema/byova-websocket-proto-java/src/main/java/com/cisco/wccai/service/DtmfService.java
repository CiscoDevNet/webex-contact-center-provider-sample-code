package com.cisco.wccai.service;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.util.AudioFileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAInputMode.INPUT_VOICE_DTMF;
import static com.cisco.wccai.common.AudioConstant.EIGHT_AUDIO;
import static com.cisco.wccai.common.AudioConstant.FIVE_AUDIO;
import static com.cisco.wccai.common.AudioConstant.FOUR_AUDIO;
import static com.cisco.wccai.common.AudioConstant.NINE_AUDIO;
import static com.cisco.wccai.common.AudioConstant.ONE_AUDIO;
import static com.cisco.wccai.common.AudioConstant.SEVEN_AUDIO;
import static com.cisco.wccai.common.AudioConstant.SIX_AUDIO;
import static com.cisco.wccai.common.AudioConstant.STAR_AUDIO;
import static com.cisco.wccai.common.AudioConstant.THREE_AUDIO;
import static com.cisco.wccai.common.AudioConstant.TWO_AUDIO;
import static com.cisco.wccai.common.AudioConstant.YOU_PRESSED;
import static com.cisco.wccai.common.AudioConstant.ZERO_AUDIO;

@Slf4j
@Service
@RequiredArgsConstructor
public class DtmfService {
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;
    private boolean isCallEndInputReceived = false;
    private boolean isCallTransferInputReceived = false;

    public void processDtmfInput(Voicevirtualagent.VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        String conversationId = voiceVARequest.getConversationId();
        log.info("Received DTMF input for conversationId: {}", conversationId);
        List<ByovaCommon.DTMFDigits> dtmfDigits = voiceVARequest.getDtmfInput().getDtmfEventsList();

        if (dtmfDigits.isEmpty()) {
            log.info("Received empty DTMF input for conversationId: {}", conversationId);
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.noInputEventResponse());
            return;
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

    public void mapDtmfInputToEvents(String conversationId, List<ByovaCommon.DTMFDigits> dtmfDigits, WebSocketSession session) throws IOException {
        virtualAgentService.sendMessage(session,
                virtualAgentAdaptor.dtmfVaResponse(AudioFileUtil.audioContentFromResources(YOU_PRESSED), INPUT_VOICE_DTMF));
        for (ByovaCommon.DTMFDigits dtmfDigit : dtmfDigits) {
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
