package com.cisco.wccai.service;

import com.cisco.wccai.common.AudioConstant;
import com.cisco.wccai.util.AudioFileUtil;
import com.cisco.wccai.ws.voice.*;
import com.cisco.wccai.ws.voice.constant.DTMFDigits;
import com.cisco.wccai.ws.voice.constant.OutputEventType;
import com.cisco.wccai.ws.voice.constant.ResponseType;
import com.cisco.wccai.ws.voice.constant.VoiceVAInputMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.cisco.wccai.common.AudioConstant.*;
import static com.cisco.wccai.ws.voice.constant.MessageType.VOICE_VA_RESPONSE;
import static com.cisco.wccai.ws.voice.constant.OutputEventType.SESSION_END;
import static com.cisco.wccai.ws.voice.constant.OutputEventType.TRANSFER_TO_AGENT;
import static com.cisco.wccai.ws.voice.constant.VoiceVAInputMode.INPUT_VOICE_DTMF;


@Slf4j
@Service
public class VirtualAgentAdaptor {
    // input_handling_config / speech timers. This is the timeout before the first voice or DTMF input.
    @Value("${voice.va.no-input.timeout-millis}")
    int noInputTimeoutMillis;

    // barge-in is a call-level policy; keep the same value for every prompt in the turn.
    @Value("${voice.va.barge-in-enabled:false}")
    boolean bargeInEnabled;

    @Value("${voice.va.dtmf.input-length}")
    int dtmfInputLength;

    @Value("${voice.va.dtmf.inter-digit-timeout-millis}")
    int dtmfInterDigitTimeoutMillis;

    // Terminating character for DTMF input, default is 16 (which is the '#' key)
    @Value("${voice.va.dtmf.term-char}")
    String dtmfTermChar;

    public WsEnvelopeVoiceVAResponse callStartEventResponse() {
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(VoiceVAResponse.builder()
                        .setPrompts(List.of(preparePromptWithFile(CALL_START_AUDIO, bargeInEnabled)))
                        .setInputMode(INPUT_VOICE_DTMF)
                        .setInputHandlingConfig(inputHandlingConfig())
                        .setSessionTranscript(textContent("Call started"))
                        .setSessionSummary(textContent("Virtual agent call session initialized"))
                        .setResponseType(ResponseType.FINAL)
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public WsEnvelopeVoiceVAResponse callEndEventResponse() {
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(VoiceVAResponse.builder()
                        .setPrompts(List.of(preparePromptWithFile(CALL_END_AUDIO, bargeInEnabled)))
                        .setSessionTranscript(textContent("Call ended"))
                        .setSessionSummary(textContent("Virtual agent call session completed"))
                        .setResponseType(ResponseType.FINAL)
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public WsEnvelopeVoiceVAResponse noInputEventResponse() {
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(VoiceVAResponse.builder()
                        .setPrompts(List.of(preparePromptWithFile(NO_INPUT_AUDIO, bargeInEnabled)))
                        .setInputMode(INPUT_VOICE_DTMF)
                        .setInputHandlingConfig(inputHandlingConfig())
                        .setSessionTranscript(textContent("No input detected"))
                        .setSessionSummary(textContent("No user input received, playing prompt"))
                        .setResponseType(ResponseType.FINAL)
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public WsEnvelopeVoiceVAResponse callEndEvent() {
        // TODO: Close the WebSocket after this terminal exit event is sent and flushed.
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(outputEventResponseBuilder(preparePromptWithFile(CALL_END_AUDIO, bargeInEnabled), SESSION_END)
                        .setSessionTranscript(textContent("Session end event"))
                        .setSessionSummary(textContent("Call terminated due to session end request"))
                        .setResponseType(ResponseType.FINAL)
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public WsEnvelopeVoiceVAResponse agentTransferEvent() {
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(outputEventResponseBuilder(preparePromptWithFile(AGENT_TRANSFER_AUDIO, bargeInEnabled), TRANSFER_TO_AGENT)
                        .setSessionTranscript(textContent("Transferring call to live agent"))
                        .setSessionSummary(textContent("Agent transfer initiated"))
                        .setResponseType(ResponseType.FINAL)
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public InputHandlingConfig inputHandlingConfig() {
        return inputHandlingConfig(dtmfInputLength, dtmfInterDigitTimeoutMillis, DTMFDigits.valueOf(dtmfTermChar), noInputTimeoutMillis);
    }

    public WsEnvelopeVoiceVAResponse prepareAudioResponse(String audioContent, VoiceVAInputMode inputMode, ResponseType responseType) {
        String transcriptText = responseType == ResponseType.FINAL ? "User speech processed" : "Processing user speech";
        String summaryText = responseType == ResponseType.FINAL ? "Final response sent to client" : "Streaming response chunk delivered";
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(VoiceVAResponse.builder()
                        .setPrompts(List.of(preparePrompt(audioContent, bargeInEnabled)))
                        .setInputMode(inputMode)
                        // every response, including CHUNK responses, carries input_handling_config.
                        .setInputHandlingConfig(inputHandlingConfig())
                        .setResponseType(responseType)
                        .setSessionTranscript(textContent(transcriptText))
                        .setSessionSummary(textContent(summaryText))
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public WsEnvelopeVoiceVAResponse dtmfVaResponse(String audioContent, VoiceVAInputMode inputMode) {
        return dtmfVaResponse(List.of(audioContent), inputMode, null);
    }

    public WsEnvelopeVoiceVAResponse dtmfVaResponse(List<String> audioContents,
                                                    VoiceVAInputMode inputMode,
                                                    OutputEventType terminalEvent) {
        VoiceVAResponse.VoiceVAResponseBuilder responseBuilder = VoiceVAResponse.builder()
                        .setPrompts(audioContents.stream()
                                .map(audioContent -> preparePrompt(audioContent, bargeInEnabled))
                                .toList())
                        .setInputMode(inputMode)
                        .setInputHandlingConfig(inputHandlingConfig())
                        .setSessionTranscript(textContent("DTMF prompt playback"))
                        .setSessionSummary(textContent("DTMF guidance audio returned"))
                        .setResponseType(ResponseType.FINAL);
        if (terminalEvent != null) {
            // TODO: Close the WebSocket after this terminal output event is sent and flushed.
            responseBuilder.setOutputEvents(List.of(outputEvent(terminalEvent)));
        }
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(responseBuilder.build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public WsEnvelopeVoiceVAResponse prepareVAResponse(OutputEventType outputEvent) {
        return WsEnvelopeVoiceVAResponse.builder()
                .setPayload(VoiceVAResponse
                        .builder()
                        .setOutputEvents(List.of(outputEvent(outputEvent)))
                        .setSessionTranscript(textContent("Voice VA event: " + outputEvent.name()))
                        .setSessionSummary(textContent("Output event dispatched to client"))
                        .setResponseType(ResponseType.FINAL)
                        .build())
                .setType(VOICE_VA_RESPONSE.name())
                .build();
    }

    public VoiceVAResponse.VoiceVAResponseBuilder outputEventResponseBuilder(Prompt prompt, OutputEventType outputEvent) {
        return VoiceVAResponse.builder()
                .setPrompts(List.of(prompt))
                .setOutputEvents(List.of(outputEvent(outputEvent)));
    }

    public Prompt preparePromptWithFile(String audioFileName, boolean isBargeInEnabled) {
        return preparePrompt(AudioFileUtil.audioToBase64String(AudioConstant.BASE_PATH + audioFileName), isBargeInEnabled);
    }

    public Prompt preparePrompt(String audioContent, boolean isBargeInEnabled) {
        Prompt.PromptBuilder promptBuilder = Prompt.builder();

        if (Objects.nonNull(audioContent)) {
            promptBuilder.setAudioContentB64(audioContent);
        }
        promptBuilder.setIsBargeInEnabled(isBargeInEnabled);
        return promptBuilder.build();
    }

    public InputHandlingConfig inputHandlingConfig(int dtmfInputLength, int interDigitTimeoutMillis, DTMFDigits termChar, int noInputTimeoutMillis) {
        // Webex collects DTMF as one batch. The sample intentionally advertises 9 digit,
        // a 5000 ms inter-digit timeout, the configured terminator, and only the no-input speech timer.
        return InputHandlingConfig.builder()
                .setDtmfConfig(dtmfInputConfig(dtmfInputLength, interDigitTimeoutMillis, termChar))
                .setSpeechTimers(InputSpeechTimers.builder()
                        .setNoInputTimeoutMsec(noInputTimeoutMillis)
                        .build())
                .build();
    }

    public DTMFInputConfig dtmfInputConfig(int dtmfInputLength, int interDigitTimeoutMillis, DTMFDigits termChar) {
        return DTMFInputConfig.builder()
                .setDtmfInputLength(dtmfInputLength)
                .setInterDigitTimeoutMsec(interDigitTimeoutMillis)
                .setTermchar(termChar)
                .build();
    }

    public OutputEvent outputEvent(OutputEventType eventType) {
        return OutputEvent.builder()
                .setEventType(eventType)
                .build();
    }

    private TextContent textContent() {
        return textContent("Virtual agent simulator session");
    }

    private TextContent textContent(String transcriptText) {
        return TextContent.builder()
                .setLanguageCode("en-US")
                .setText(transcriptText != null ? transcriptText : "")
                .build();
    }
}
