package com.cisco.wccai.byova.service;

import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.SESSION_END;
import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.TRANSFER_TO_AGENT;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAInputMode.INPUT_VOICE_DTMF;
import static com.cisco.wccai.byova.audio.AudioConstants.AGENT_TRANSFER_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.CALL_END_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.CALL_START_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.NO_INPUT_AUDIO;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.byova.audio.AudioFileLoader;
import com.cisco.wccai.byova.config.VoiceVaProperties;
import com.google.protobuf.ByteString;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stateless builder for {@link Voicevirtualagent.VoiceVAResponse} messages. Consolidates the
 * protobuf construction logic so per-call handlers can stay focused on orchestration.
 */
@Component
public class VoiceVAResponseBuilder {

    private final VoiceVaProperties properties;

    public VoiceVAResponseBuilder(VoiceVaProperties properties) {
        this.properties = properties;
    }

    /* ---------- event-style responses ---------- */

    public Voicevirtualagent.VoiceVAResponse callStartEventResponse() {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePrompt(CALL_START_AUDIO, true))
                .setInputMode(INPUT_VOICE_DTMF)
                .setInputHandlingConfig(inputHandlingConfig())
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse noInputEventResponse() {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePrompt(NO_INPUT_AUDIO, true))
                .setInputMode(INPUT_VOICE_DTMF)
                .setInputHandlingConfig(inputHandlingConfig())
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse callEndEvent() {
        return outputEventResponse(preparePrompt(CALL_END_AUDIO, true), SESSION_END);
    }

    public Voicevirtualagent.VoiceVAResponse agentTransferEvent() {
        return outputEventResponse(preparePrompt(AGENT_TRANSFER_AUDIO, true), TRANSFER_TO_AGENT);
    }

    public Voicevirtualagent.VoiceVAResponse outputEventOnlyResponse(ByovaCommon.OutputEvent.EventType eventType) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addOutputEvents(outputEvent(eventType))
                .build();
    }

    /* ---------- audio / DTMF responses ---------- */

    public Voicevirtualagent.VoiceVAResponse audioResponse(
            ByteString audioContent,
            Voicevirtualagent.VoiceVAInputMode inputMode,
            Voicevirtualagent.VoiceVAResponse.ResponseType responseType) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePrompt(audioContent, true))
                .setInputMode(inputMode)
                .setResponseType(responseType)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse dtmfResponse(
            ByteString audioContent, Voicevirtualagent.VoiceVAInputMode inputMode) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePrompt(audioContent, true))
                .setInputMode(inputMode)
                .setInputHandlingConfig(inputHandlingConfig())
                .build();
    }

    /* ---------- ListVA ---------- */

    public ByovaCommon.ListVAResponse sampleVirtualAgents() {
        List<ByovaCommon.VirtualAgentInfo> virtualAgents =
                List.of(ByovaCommon.VirtualAgentInfo.newBuilder()
                        .setVirtualAgentId("1")
                        .setVirtualAgentName("Virtual Agent")
                        .build());
        return ByovaCommon.ListVAResponse.newBuilder()
                .addAllVirtualAgents(virtualAgents)
                .build();
    }

    /* ---------- helpers ---------- */

    public Voicevirtualagent.Prompt preparePrompt(String audioFileName, boolean bargeInEnabled) {
        return preparePrompt(AudioFileLoader.audioContentFromResources(audioFileName), bargeInEnabled);
    }

    public Voicevirtualagent.Prompt preparePrompt(ByteString audioContent, boolean bargeInEnabled) {
        Voicevirtualagent.Prompt.Builder builder = Voicevirtualagent.Prompt.newBuilder()
                .setIsBargeInEnabled(bargeInEnabled);
        if (audioContent != null) {
            builder.setAudioContent(audioContent);
        }
        return builder.build();
    }

    public ByovaCommon.InputHandlingConfig inputHandlingConfig() {
        VoiceVaProperties.Dtmf dtmf = properties.dtmf();
        ByovaCommon.DTMFDigits termChar = ByovaCommon.DTMFDigits.forNumber(dtmf.termChar());
        if (termChar == null) {
            throw new IllegalStateException(
                    "Invalid voice.va.dtmf.term-char: " + dtmf.termChar()
                            + ". Expected a valid ByovaCommon.DTMFDigits enum number.");
        }
        ByovaCommon.DTMFInputConfig dtmfConfig = ByovaCommon.DTMFInputConfig.newBuilder()
                .setDtmfInputLength(dtmf.inputLength())
                .setInterDigitTimeoutMsec(dtmf.interDigitTimeoutMillis())
                .setTermchar(termChar)
                .build();
        ByovaCommon.InputSpeechTimers timers = ByovaCommon.InputSpeechTimers.newBuilder()
                .setCompleteTimeoutMsec(properties.inputTimeoutMillis())
                .setIncompleteTimeoutMsec(properties.inputTimeoutMillis())
                .build();
        return ByovaCommon.InputHandlingConfig.newBuilder()
                .setDtmfConfig(dtmfConfig)
                .setSpeechTimers(timers)
                .build();
    }

    private Voicevirtualagent.VoiceVAResponse outputEventResponse(
            Voicevirtualagent.Prompt prompt, ByovaCommon.OutputEvent.EventType eventType) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(prompt)
                .addOutputEvents(outputEvent(eventType))
                .build();
    }

    private ByovaCommon.OutputEvent outputEvent(ByovaCommon.OutputEvent.EventType eventType) {
        return ByovaCommon.OutputEvent.newBuilder().setEventType(eventType).build();
    }
}
