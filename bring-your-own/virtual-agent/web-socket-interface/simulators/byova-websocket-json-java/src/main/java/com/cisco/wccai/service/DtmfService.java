package com.cisco.wccai.service;

import com.cisco.wccai.util.AudioFileUtil;
import com.cisco.wccai.ws.voice.DTMFInputsWrapper;
import com.cisco.wccai.ws.voice.VoiceVARequest;
import com.cisco.wccai.ws.voice.WsEnvelopeVoiceVAResponse;
import com.cisco.wccai.ws.voice.constant.DTMFDigits;
import com.cisco.wccai.ws.voice.constant.OutputEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.cisco.wccai.common.AudioConstant.*;
import static com.cisco.wccai.ws.voice.constant.VoiceVAInputMode.INPUT_VOICE_DTMF;
import static com.cisco.wccai.ws.voice.constant.OutputEventType.SESSION_END;
import static com.cisco.wccai.ws.voice.constant.OutputEventType.TRANSFER_TO_AGENT;


@Slf4j
@Service
@RequiredArgsConstructor
public class DtmfService {
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;

    public void processDtmfInput(VoiceVARequest voiceVARequest, WebSocketSession session) throws IOException {
        String conversationId = voiceVARequest.getConversationId();
        log.info("Received DTMF input for conversationId: {}", conversationId);
        DTMFInputsWrapper dtmfInputsWrapper = (DTMFInputsWrapper) voiceVARequest.getVoiceVaInputType();
        List<DTMFDigits> dtmfDigits = dtmfInputsWrapper.getDtmfInput().getDtmfEvents();

        if (dtmfDigits.isEmpty()) {
            log.info("Received empty DTMF input for conversationId: {}", conversationId);
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.noInputEventResponse());
            return;
        }

        // A DTMF collection is one input turn. Return one FINAL response for the complete collection.
        virtualAgentService.sendMessage(session, mapDtmfInputToResponse(conversationId, dtmfDigits));
    }

    private WsEnvelopeVoiceVAResponse mapDtmfInputToResponse(String conversationId, List<DTMFDigits> dtmfDigits) {
        // Blog: Webex sends the collected digits together, in order; do not return one FINAL per digit.
        List<String> audioContents = new ArrayList<>();
        audioContents.add(AudioFileUtil.audioContentFromResources(YOU_PRESSED));
        OutputEventType terminalEvent = null;

        for (DTMFDigits dtmfDigit : dtmfDigits) {
            switch (dtmfDigit) {
                case DTMF_DIGIT_ONE -> audioContents.add(AudioFileUtil.audioContentFromResources(ONE_AUDIO));
                case DTMF_DIGIT_TWO -> audioContents.add(AudioFileUtil.audioContentFromResources(TWO_AUDIO));
                case DTMF_DIGIT_THREE -> audioContents.add(AudioFileUtil.audioContentFromResources(THREE_AUDIO));
                case DTMF_DIGIT_FOUR -> audioContents.add(AudioFileUtil.audioContentFromResources(FOUR_AUDIO));
                case DTMF_DIGIT_FIVE -> {
                    audioContents.add(AudioFileUtil.audioContentFromResources(FIVE_AUDIO));
                    audioContents.add(AudioFileUtil.audioContentFromResources(AGENT_TRANSFER_AUDIO));
                    terminalEvent = TRANSFER_TO_AGENT;
                }
                case DTMF_DIGIT_SIX -> audioContents.add(AudioFileUtil.audioContentFromResources(SIX_AUDIO));
                case DTMF_DIGIT_SEVEN -> audioContents.add(AudioFileUtil.audioContentFromResources(SEVEN_AUDIO));
                case DTMF_DIGIT_EIGHT -> audioContents.add(AudioFileUtil.audioContentFromResources(EIGHT_AUDIO));
                case DTMF_DIGIT_NINE -> audioContents.add(AudioFileUtil.audioContentFromResources(NINE_AUDIO));
                case DTMF_DIGIT_ZERO -> audioContents.add(AudioFileUtil.audioContentFromResources(ZERO_AUDIO));
                case DTMF_DIGIT_STAR -> {
                    audioContents.add(AudioFileUtil.audioContentFromResources(STAR_AUDIO));
                    audioContents.add(AudioFileUtil.audioContentFromResources(CALL_END_AUDIO));
                    terminalEvent = SESSION_END;
                }
                default ->
                        log.info("Received unknown DTMF digit: {} for conversationId: {}", dtmfDigit, conversationId);
            }
        }
        return virtualAgentAdaptor.dtmfVaResponse(audioContents, INPUT_VOICE_DTMF, terminalEvent);
    }

}
