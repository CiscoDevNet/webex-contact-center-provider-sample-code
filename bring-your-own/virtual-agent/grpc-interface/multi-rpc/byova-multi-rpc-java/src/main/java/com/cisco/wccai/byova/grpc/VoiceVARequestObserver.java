package com.cisco.wccai.byova.grpc;

import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.END_OF_INPUT;
import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.START_OF_INPUT;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAInputMode.INPUT_VOICE_DTMF;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAResponse.ResponseType.CHUNK;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAResponse.ResponseType.FINAL;
import static com.cisco.wccai.byova.audio.AudioConstants.EIGHT_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.FIVE_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.FOUR_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.NINE_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.ONE_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.SERVICE_REQUEST_RAISED;
import static com.cisco.wccai.byova.audio.AudioConstants.SEVEN_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.SIX_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.STAR_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.THREE_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.TWO_AUDIO;
import static com.cisco.wccai.byova.audio.AudioConstants.WAIT_FOR_SERVICE_REQUEST;
import static com.cisco.wccai.byova.audio.AudioConstants.YOU_PRESSED;
import static com.cisco.wccai.byova.audio.AudioConstants.ZERO_AUDIO;

import com.cisco.wcc.ccai.media.v1.ByovaCommon;
import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.byova.audio.AudioFileLoader;
import com.cisco.wccai.byova.config.VoiceVaProperties;
import com.cisco.wccai.byova.exception.AudioProcessingException;
import com.cisco.wccai.byova.service.SilenceDetector;
import com.cisco.wccai.byova.service.VoiceVAResponseBuilder;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Per-RPC observer that collects caller input from a bidirectional streaming call and emits
 * responses through the supplied {@link StreamObserver}. One instance is created per call, so it
 * safely holds per-conversation state (audio buffers, speech markers, DTMF flags).
 */
@Slf4j
class VoiceVARequestObserver implements StreamObserver<Voicevirtualagent.VoiceVARequest> {

    private final StreamObserver<Voicevirtualagent.VoiceVAResponse> responseObserver;
    private final VoiceVAResponseBuilder responseBuilder;
    private final SilenceDetector silenceDetector;
    private final VoiceVaProperties properties;

    private final String rpcId;
    private String conversationId;
    private Voicevirtualagent.VoiceVARequest lastRequest;

    // Audio streaming state
    private final ByteArrayOutputStream callerAudioChunkBuffer = new ByteArrayOutputStream();
    private final ByteArrayOutputStream callerAudioBuffer = new ByteArrayOutputStream();
    private boolean isStartOfInputSent = false;

    // DTMF outcome flags
    private boolean callEndRequested = false;
    private boolean callTransferRequested = false;

    VoiceVARequestObserver(
            StreamObserver<Voicevirtualagent.VoiceVAResponse> responseObserver,
            VoiceVAResponseBuilder responseBuilder,
            SilenceDetector silenceDetector,
            VoiceVaProperties properties) {
        this.responseObserver = responseObserver;
        this.responseBuilder = responseBuilder;
        this.silenceDetector = silenceDetector;
        this.properties = properties;
        this.rpcId = GrpcContextHelper.getCurrentRpcId();
        log.info("New Voice VA conversation started, rpcId: {}", rpcId);
    }

    @Override
    public void onNext(Voicevirtualagent.VoiceVARequest request) {
        this.lastRequest = request;
        this.conversationId = request.getConversationId();
        Voicevirtualagent.VoiceVARequest.VoiceVaInputTypeCase inputType = request.getVoiceVaInputTypeCase();
        switch (inputType) {
            case AUDIO_INPUT -> processAudioInput(request);
            case EVENT_INPUT -> processEventInput(request);
            case DTMF_INPUT -> processDtmfInput(request);
            default -> log.warn(
                    "Unknown Voice VA input type ({}) for conversationId: {}", inputType, conversationId);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("onError for conversationId: {}, rpcId: {}", conversationId, rpcId, throwable);
        responseObserver.onError(throwable);
    }

    @Override
    public void onCompleted() {
        ByovaCommon.EventInput.EventType eventType = (lastRequest != null)
                ? lastRequest.getEventInput().getEventType()
                : ByovaCommon.EventInput.EventType.UNRECOGNIZED;
        log.info("onCompleted for conversationId: {}, rpcId: {}, lastEvent: {}",
                conversationId, rpcId, eventType);
        responseObserver.onCompleted();
    }

    /* ---------- event input ---------- */

    private void processEventInput(Voicevirtualagent.VoiceVARequest request) {
        ByovaCommon.EventInput.EventType eventType = request.getEventInput().getEventType();
        log.info("Received {} event for conversationId: {}", eventType, conversationId);

        switch (eventType) {
            case SESSION_START -> responseObserver.onNext(responseBuilder.callStartEventResponse());
            case SESSION_END -> log.info("Session ended for conversationId: {}", conversationId);
            case NO_INPUT -> responseObserver.onNext(responseBuilder.noInputEventResponse());
            case CUSTOM_EVENT -> handleCustomEvent(request);
            default -> log.warn("Ignoring event type: {} for conversationId: {}", eventType, conversationId);
        }
    }

    private void handleCustomEvent(Voicevirtualagent.VoiceVARequest request) {
        String customEventName = request.getEventInput().getName();
        log.info("Processing CUSTOM_EVENT with name: {} for conversationId: {}", customEventName, conversationId);
        Struct parameters = request.getEventInput().getParameters();
        for (Map.Entry<String, Value> entry : parameters.getFieldsMap().entrySet()) {
            log.info("  custom parameter: {} = {}", entry.getKey(), entry.getValue().getStringValue());
        }
        responseObserver.onNext(responseBuilder.callStartEventResponse());
    }

    /* ---------- audio input ---------- */

    private void processAudioInput(Voicevirtualagent.VoiceVARequest request) {
        byte[] callerAudio = request.getAudioInput().getCallerAudio().toByteArray();
        VoiceVaProperties.Audio audioCfg = properties.audio();

        if (callerAudio.length <= audioCfg.ignoreBufferSize()) {
            log.debug("Ignoring initial audio smaller than {} bytes", audioCfg.ignoreBufferSize());
            return;
        }

        try {
            callerAudioChunkBuffer.write(callerAudio);

            int bufferSizeToProcess =
                    audioCfg.useChunkedAudio() ? audioCfg.chunkBufferSize() : audioCfg.wavBufferSize();
            if (callerAudioChunkBuffer.size() < bufferSizeToProcess) {
                return;
            }

            byte[] currentChunk = callerAudioChunkBuffer.toByteArray();
            callerAudioChunkBuffer.reset();

            if (silenceDetector.isSilence(currentChunk, audioCfg.amplitudeThreshold())) {
                processBufferedAudio();
            } else {
                sendStartOfInputIfNeeded();
                log.info("Caller is speaking. Buffering chunk ({} bytes).", currentChunk.length);
                callerAudioBuffer.write(currentChunk);
            }
        } catch (Exception e) {
            log.error("Error processing audio stream (chunked={})", audioCfg.useChunkedAudio(), e);
            throw new AudioProcessingException("Error processing audio stream.", e);
        }
    }

    private void processBufferedAudio() {
        if (callerAudioBuffer.size() == 0) {
            log.info("Silence detected, but no buffered audio to process.");
            return;
        }
        sendEndOfInputIfNeeded();

        VoiceVaProperties.Audio audioCfg = properties.audio();
        byte[] buffered = callerAudioBuffer.toByteArray();
        callerAudioBuffer.reset();

        if (audioCfg.useChunkedAudio()) {
            log.info("Silence detected after speech. Processing chunked user audio ({} bytes).", buffered.length);
            emitChunkedAudioResponses(buffered);
        } else {
            log.info("Silence detected after speech. Processing wav user audio ({} bytes).", buffered.length);
            emitWavAudioResponse(buffered);
        }

        if (audioCfg.writeToFile()) {
            AudioFileLoader.writeWavWithMuLaw(buffered);
        }
        responseObserver.onCompleted();
    }

    private void emitWavAudioResponse(byte[] bufferedAudio) {
        ByteString wavContent = ByteString.copyFrom(AudioFileLoader.getMuLawWavHeader())
                .concat(ByteString.copyFrom(bufferedAudio));
        responseObserver.onNext(
                responseBuilder.audioResponse(wavContent, INPUT_VOICE_DTMF, FINAL));
    }

    private void emitChunkedAudioResponses(byte[] currentUlawAudioBuffer) {
        responseObserver.onNext(responseBuilder.audioResponse(
                ByteString.copyFrom(currentUlawAudioBuffer), INPUT_VOICE_DTMF, CHUNK));
        responseObserver.onNext(responseBuilder.audioResponse(
                AudioFileLoader.audioContentFromResources(WAIT_FOR_SERVICE_REQUEST), INPUT_VOICE_DTMF, CHUNK));
        responseObserver.onNext(responseBuilder.audioResponse(
                AudioFileLoader.audioContentFromResources(SERVICE_REQUEST_RAISED), INPUT_VOICE_DTMF, CHUNK));
        responseObserver.onNext(responseBuilder.audioResponse(
                ByteString.EMPTY, INPUT_VOICE_DTMF, FINAL));
    }

    private void sendStartOfInputIfNeeded() {
        if (!isStartOfInputSent) {
            isStartOfInputSent = true;
            log.info("Sending START_OF_INPUT event to client for conversationId: {}", conversationId);
            responseObserver.onNext(responseBuilder.outputEventOnlyResponse(START_OF_INPUT));
        }
    }

    private void sendEndOfInputIfNeeded() {
        if (isStartOfInputSent) {
            isStartOfInputSent = false;
            log.info("Sending END_OF_INPUT event to client for conversationId: {}", conversationId);
            responseObserver.onNext(responseBuilder.outputEventOnlyResponse(END_OF_INPUT));
        }
    }

    /* ---------- dtmf input ---------- */

    private void processDtmfInput(Voicevirtualagent.VoiceVARequest request) {
        log.info("Received DTMF input for conversationId: {}", conversationId);
        List<ByovaCommon.DTMFDigits> digits = request.getDtmfInput().getDtmfEventsList();

        if (digits.isEmpty()) {
            log.info("Empty DTMF input for conversationId: {}", conversationId);
            responseObserver.onNext(responseBuilder.noInputEventResponse());
            return;
        }

        responseObserver.onNext(responseBuilder.dtmfResponse(
                AudioFileLoader.audioContentFromResources(YOU_PRESSED), INPUT_VOICE_DTMF));

        for (ByovaCommon.DTMFDigits digit : digits) {
            String audioFile = digitToAudioFile(digit);
            if (audioFile == null) {
                log.info("Ignoring unknown DTMF digit: {} for conversationId: {}", digit, conversationId);
                continue;
            }
            responseObserver.onNext(responseBuilder.dtmfResponse(
                    AudioFileLoader.audioContentFromResources(audioFile), INPUT_VOICE_DTMF));
        }

        if (callEndRequested) {
            responseObserver.onNext(responseBuilder.callEndEvent());
        } else if (callTransferRequested) {
            responseObserver.onNext(responseBuilder.agentTransferEvent());
        }
    }

    private String digitToAudioFile(ByovaCommon.DTMFDigits digit) {
        return switch (digit) {
            case DTMF_DIGIT_ONE -> ONE_AUDIO;
            case DTMF_DIGIT_TWO -> TWO_AUDIO;
            case DTMF_DIGIT_THREE -> THREE_AUDIO;
            case DTMF_DIGIT_FOUR -> FOUR_AUDIO;
            case DTMF_DIGIT_FIVE -> FIVE_AUDIO;
            case DTMF_DIGIT_SIX -> SIX_AUDIO;
            case DTMF_DIGIT_SEVEN -> SEVEN_AUDIO;
            case DTMF_DIGIT_EIGHT -> EIGHT_AUDIO;
            case DTMF_DIGIT_NINE -> NINE_AUDIO;
            case DTMF_DIGIT_ZERO -> {
                callTransferRequested = true;
                yield ZERO_AUDIO;
            }
            case DTMF_DIGIT_STAR -> {
                callEndRequested = true;
                yield STAR_AUDIO;
            }
            default -> null;
        };
    }
}
