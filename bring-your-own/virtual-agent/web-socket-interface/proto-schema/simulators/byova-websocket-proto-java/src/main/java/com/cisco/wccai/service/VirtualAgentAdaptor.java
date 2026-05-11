package com.cisco.wccai.service;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.common.AudioConstant;
import com.cisco.wccai.util.AudioFileUtil;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.SESSION_END;
import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.TRANSFER_TO_AGENT;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAInputMode.INPUT_VOICE_DTMF;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAResponse.ResponseType.FINAL;
import static com.cisco.wccai.common.AudioConstant.AGENT_TRANSFER_AUDIO;
import static com.cisco.wccai.common.AudioConstant.CALL_END_AUDIO;
import static com.cisco.wccai.common.AudioConstant.CALL_START_AUDIO;
import static com.cisco.wccai.common.AudioConstant.NO_INPUT_AUDIO;

/**
 * Builds {@link Voicevirtualagent.VoiceVAResponse} protobuf messages for the common simulator
 * scenarios (call start/end, no input, audio chunks, DTMF prompts, output events, agent transfer).
 *
 * <p>This class mirrors the JSON-schema {@code VirtualAgentAdaptor} but returns the raw protobuf
 * messages directly instead of wrapping them in a JSON envelope, since the WebSocket frame type
 * and endpoint already disambiguate requests from responses.</p>
 */
@Slf4j
@Service
public class VirtualAgentAdaptor {

    @Value("${voice.va.input.timeout-millis}")
    int inputTimeoutMillis;

    @Value("${voice.va.no-input.timeout-millis}")
    int noInputTimeoutMillis;

    @Value("${voice.va.dtmf.input-length}")
    int dtmfInputLength;

    @Value("${voice.va.dtmf.inter-digit-timeout-millis}")
    int dtmfInterDigitTimeoutMillis;

    // Terminating character for DTMF input, default is DTMF_DIGIT_POUND ('#')
    @Value("${voice.va.dtmf.term-char}")
    String dtmfTermChar;

    public Voicevirtualagent.VoiceVAResponse callStartEventResponse() {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePromptWithFile(CALL_START_AUDIO, true))
                .setInputMode(INPUT_VOICE_DTMF)
                .setInputHandlingConfig(inputHandlingConfig())
                .setSessionTranscript(textContent("Call started"))
                .setSessionSummary(textContent("Virtual agent call session initialized"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse callEndEventResponse() {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePromptWithFile(CALL_END_AUDIO, false))
                .setSessionTranscript(textContent("Call ended"))
                .setSessionSummary(textContent("Virtual agent call session completed"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse noInputEventResponse() {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePromptWithFile(NO_INPUT_AUDIO, false))
                .setInputMode(INPUT_VOICE_DTMF)
                .setInputHandlingConfig(inputHandlingConfig())
                .setSessionTranscript(textContent("No input detected"))
                .setSessionSummary(textContent("No user input received, playing prompt"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse callEndEvent() {
        return outputEventResponseBuilder(preparePromptWithFile(CALL_END_AUDIO, false), SESSION_END)
                .setSessionTranscript(textContent("Session end event"))
                .setSessionSummary(textContent("Call terminated due to session end request"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse agentTransferEvent() {
        return outputEventResponseBuilder(preparePromptWithFile(AGENT_TRANSFER_AUDIO, false), TRANSFER_TO_AGENT)
                .setSessionTranscript(textContent("Transferring call to live agent"))
                .setSessionSummary(textContent("Agent transfer initiated"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse prepareAudioResponse(ByteString audioContent,
                                                                  Voicevirtualagent.VoiceVAInputMode inputMode,
                                                                  Voicevirtualagent.VoiceVAResponse.ResponseType responseType) {
        String transcriptText = responseType == FINAL ? "User speech processed" : "Processing user speech";
        String summaryText = responseType == FINAL ? "Final response sent to client" : "Streaming response chunk delivered";
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePrompt(audioContent, false))
                .setInputMode(inputMode)
                .setResponseType(responseType)
                .setSessionTranscript(textContent(transcriptText))
                .setSessionSummary(textContent(summaryText))
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse dtmfVaResponse(ByteString audioContent,
                                                            Voicevirtualagent.VoiceVAInputMode inputMode) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(preparePrompt(audioContent, false))
                .setInputMode(inputMode)
                .setInputHandlingConfig(inputHandlingConfig())
                .setSessionTranscript(textContent("DTMF prompt playback"))
                .setSessionSummary(textContent("DTMF guidance audio returned"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse prepareVAResponse(ByovaCommon.OutputEvent.EventType outputEvent) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addOutputEvents(outputEvent(outputEvent))
                .setSessionTranscript(textContent("Voice VA event: " + outputEvent.name()))
                .setSessionSummary(textContent("Output event dispatched to client"))
                .setResponseType(FINAL)
                .build();
    }

    public Voicevirtualagent.VoiceVAResponse.Builder outputEventResponseBuilder(
            Voicevirtualagent.Prompt prompt, ByovaCommon.OutputEvent.EventType outputEvent) {
        return Voicevirtualagent.VoiceVAResponse.newBuilder()
                .addPrompts(prompt)
                .addOutputEvents(outputEvent(outputEvent));
    }

    public Voicevirtualagent.Prompt preparePromptWithFile(String audioFileName, boolean isBargeInEnabled) {
        return preparePrompt(AudioFileUtil.audioContentFromResources(audioFileName), isBargeInEnabled);
    }

    public Voicevirtualagent.Prompt preparePrompt(ByteString audioContent, boolean isBargeInEnabled) {
        Voicevirtualagent.Prompt.Builder promptBuilder = Voicevirtualagent.Prompt.newBuilder()
                .setIsBargeInEnabled(isBargeInEnabled);
        if (audioContent != null) {
            promptBuilder.setAudioContent(audioContent);
        }
        return promptBuilder.build();
    }

    public ByovaCommon.InputHandlingConfig inputHandlingConfig() {
        return inputHandlingConfig(dtmfInputLength, dtmfInterDigitTimeoutMillis,
                ByovaCommon.DTMFDigits.valueOf(dtmfTermChar), inputTimeoutMillis, noInputTimeoutMillis);
    }

    public ByovaCommon.InputHandlingConfig inputHandlingConfig(int dtmfInputLength,
                                                               int interDigitTimeoutMillis,
                                                               ByovaCommon.DTMFDigits termChar,
                                                               int inputTimeoutMillis,
                                                               int noInputTimeoutMillis) {
        return ByovaCommon.InputHandlingConfig.newBuilder()
                .setDtmfConfig(dtmfInputConfig(dtmfInputLength, interDigitTimeoutMillis, termChar))
                .setSpeechTimers(ByovaCommon.InputSpeechTimers.newBuilder()
                        .setCompleteTimeoutMsec(inputTimeoutMillis)
                        .setIncompleteTimeoutMsec(inputTimeoutMillis)
                        .build())
                .build();
    }

    public ByovaCommon.DTMFInputConfig dtmfInputConfig(int dtmfInputLength,
                                                       int interDigitTimeoutMillis,
                                                       ByovaCommon.DTMFDigits termChar) {
        return ByovaCommon.DTMFInputConfig.newBuilder()
                .setDtmfInputLength(dtmfInputLength)
                .setInterDigitTimeoutMsec(interDigitTimeoutMillis)
                .setTermchar(termChar)
                .build();
    }

    public ByovaCommon.OutputEvent outputEvent(ByovaCommon.OutputEvent.EventType eventType) {
        return ByovaCommon.OutputEvent.newBuilder().setEventType(eventType).build();
    }

    private ByovaCommon.TextContent textContent(String transcriptText) {
        return ByovaCommon.TextContent.newBuilder()
                .setLanguageCode("en-US")
                .setText(transcriptText != null ? transcriptText : "")
                .build();
    }

    /**
     * Convenience for callers that only have the classpath-relative filename (e.g. from
     * {@link AudioConstant}). Prefer {@link #preparePromptWithFile(String, boolean)} for clarity.
     */
    public Voicevirtualagent.Prompt promptFromResource(String resourceFileName, boolean isBargeInEnabled) {
        return preparePrompt(AudioFileUtil.audioContentFromResources(resourceFileName), isBargeInEnabled);
    }
}
