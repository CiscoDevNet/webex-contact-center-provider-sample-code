package com.cisco.wccai.service;

import com.cisco.wccai.util.AudioFileUtil;
import com.cisco.wccai.ws.voice.VoiceInputWrapper;
import com.cisco.wccai.ws.voice.WsEnvelopeError;
import com.cisco.wccai.ws.voice.WsEnvelopeVoiceVAResponse;
import com.cisco.wccai.ws.voice.constant.ErrorCode;
import com.cisco.wccai.ws.voice.constant.MessageType;
import com.cisco.wccai.ws.voice.constant.ResponseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ch.qos.logback.core.CoreConstants.EMPTY_STRING;
import static com.cisco.wccai.common.AudioConstant.SERVICE_REQUEST_RAISED;
import static com.cisco.wccai.common.AudioConstant.WAIT_FOR_SERVICE_REQUEST;
import static com.cisco.wccai.ws.voice.constant.OutputEventType.END_OF_INPUT;
import static com.cisco.wccai.ws.voice.constant.OutputEventType.START_OF_INPUT;
import static com.cisco.wccai.ws.voice.constant.VoiceVAInputMode.INPUT_VOICE_DTMF;

@Slf4j
@Service
public class AudioStreamingService {
    private final SpeechDetectionService speechDetectionService;
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;

    private static final String WAIT_FOR_SERVICE_REQUEST_AUDIO_B64 = AudioFileUtil.audioContentFromResources(WAIT_FOR_SERVICE_REQUEST);
    private static final String SERVICE_REQUEST_RAISED_AUDIO_B64 = AudioFileUtil.audioContentFromResources(SERVICE_REQUEST_RAISED);
    private static final String MU_LAW_WAV_HEADER_B64 = Base64.getEncoder().encodeToString(AudioFileUtil.getMuLawWavHeader());

    private final Map<String, AudioStreamState> audioStreamStates = new ConcurrentHashMap<>();

    @Value("${voice.va.audio.write-to-file}")
    boolean writeAudioToFile;

    @Value("${voice.va.audio.amplitude-threshold}")
    int amplitudeThreshold;

    @Value("${voice.va.audio.wav.buffer-size}")
    int wavBufferSizeToProcess;

    @Value("${voice.va.audio.chunk.buffer-size}")
    int chunkBufferSizeToProcess;

    @Value("${voice.va.audio.ignore-buffer-size}")
    int ignoreBufferSize;

    @Value("${voice.va.audio.use-chunked-audio}")
    boolean useChunkedAudio;

    public AudioStreamingService(SpeechDetectionService speechDetectionService, VirtualAgentService virtualAgentService, VirtualAgentAdaptor virtualAgentAdaptor) {
        this.speechDetectionService = speechDetectionService;
        this.virtualAgentService = virtualAgentService;
        this.virtualAgentAdaptor = virtualAgentAdaptor;
    }

    public void processAudioStream(VoiceInputWrapper voiceInputWrapper, WebSocketSession session) throws IOException {
        AudioStreamState audioStreamState = audioStreamStates.computeIfAbsent(session.getId(), key -> new AudioStreamState());
        String callerAudioB64 = voiceInputWrapper.getAudioInput().getCallerAudioB64();
        byte[] callerAudioBytes;
        try {
            // decode base64 audio input
            callerAudioBytes = Base64.getDecoder().decode(callerAudioB64);
        } catch (Exception e) {
            log.error("Error decoding base64 audio input.", e);
            WsEnvelopeError errorResponse = WsEnvelopeError.builder()
                    .setCode(ErrorCode.BAD_REQUEST)
                    .setStatus(400)
                    .setDetail(e.getMessage())
                    .build();
            virtualAgentService.sendMessage(session, errorResponse);
            return;
        }

        // ignoreBufferSize is to ignore very small audio packets that may be just noise, using silence/speech detection engine is recommended
        if (callerAudioBytes.length > ignoreBufferSize) {
            try {
                audioStreamState.callerAudioChunkBuffer.write(callerAudioBytes);

                int bufferSizeToProcess = useChunkedAudio ? chunkBufferSizeToProcess : wavBufferSizeToProcess;
                if (audioStreamState.callerAudioChunkBuffer.size() >= bufferSizeToProcess) {
                    byte[] currentChunkBuffer = audioStreamState.callerAudioChunkBuffer.toByteArray();
                    // clear the buffer to accumulate next audio chunk
                    audioStreamState.resetChunkBuffer();

                    // Using silence detection engine is recommended
                    if (speechDetectionService.isSilence(currentChunkBuffer, amplitudeThreshold)) {
                        // If the current large chunk is all silence AND there's previous audio collected
                        processBufferedAudio(session, audioStreamState);
                    } else {
                        sendStartOfInputEventIfNotSent(session, audioStreamState);
                        log.info("Caller is speaking. Adding chunks to the buffer");
                        audioStreamState.callerAudioBuffer.write(currentChunkBuffer);
                        log.info("Total buffered audio size is now {} bytes after adding chunk size is {} bytes", audioStreamState.callerAudioBuffer.size(), currentChunkBuffer.length);
                    }
                }
            } catch (Exception e) {
                log.error("Error processing audio stream. Was it chunking flow: {}", useChunkedAudio, e);
                WsEnvelopeError errorResponse = WsEnvelopeError.builder()
                        .setCode(ErrorCode.UPSTREAM_ERROR)
                        .setStatus(500)
                        .setDetail(e.getMessage())
                        .setType(MessageType.ERROR.name())
                        .build();
                virtualAgentService.sendMessage(session, errorResponse);
            }
        } else {
            log.info("Ignoring audio less than 15 bytes");
        }
    }

    private void processBufferedAudio(WebSocketSession session, AudioStreamState audioStreamState) throws IOException {
        if (audioStreamState.callerAudioBuffer.size() > 0) {
            sendEndOfInputEventIfNotSent(session, audioStreamState);

            if (useChunkedAudio) {
                log.info("Silence detected after speech. Processing chunk buffered user audio of size {}.", audioStreamState.callerAudioBuffer.size());
                processChunkAudio(session, audioStreamState.callerAudioBuffer.toByteArray());
            } else {
                log.info("Silence detected after speech. Processing wav buffered user audio of size {}.", audioStreamState.callerAudioBuffer.size());
                processWavAudio(session, audioStreamState.callerAudioBuffer.toByteArray());
            }

            // clear the buffer for next accumulation
            audioStreamState.resetAudioBuffer();
        } else {
            log.info("Silence detected, but no buffered audio to process.");
        }
    }

    // This is non-chunking flow where a single final response is sent with wav header
    private void processWavAudio(WebSocketSession session, byte[] bufferedAudio) throws IOException {
        // add wav header to the audio content
        String encodedAudioContent = Base64.getEncoder().encodeToString(bufferedAudio);
        String wavAudioContent = MU_LAW_WAV_HEADER_B64.concat(encodedAudioContent);

        // prepare VA final response with wav audio content and both input mode VOICE and DTMF
        WsEnvelopeVoiceVAResponse vaResponse = virtualAgentAdaptor.prepareAudioResponse(wavAudioContent, INPUT_VOICE_DTMF, ResponseType.FINAL);

        // send the response to the client
        virtualAgentService.sendMessage(session, vaResponse);

        // create audio file of this chunk if writeAudioToFile is true
        if (writeAudioToFile) AudioFileUtil.writeWavWithMuLaw(bufferedAudio);
    }

    // This is chunking flow where multiple chunk responses are sent for a single audio response without wav header
    private void processChunkAudio(WebSocketSession session, byte[] currentUlawAudioBuffer) throws IOException {
        String wavAudioContent = Base64.getEncoder().encodeToString(currentUlawAudioBuffer);

        // prepare and send VA CHUNK responses with raw audio content and both input mode VOICE and DTMF
        WsEnvelopeVoiceVAResponse responseChunk1 = virtualAgentAdaptor.prepareAudioResponse(wavAudioContent, INPUT_VOICE_DTMF, ResponseType.CHUNK);
        virtualAgentService.sendMessage(session, responseChunk1);

        // prepare and send VA CHUNK responses with WAIT_FOR_SERVICE_REQUEST audio content and both input mode VOICE and DTMF
        WsEnvelopeVoiceVAResponse responseChunk2 = virtualAgentAdaptor.prepareAudioResponse(WAIT_FOR_SERVICE_REQUEST_AUDIO_B64, INPUT_VOICE_DTMF, ResponseType.CHUNK);
        virtualAgentService.sendMessage(session, responseChunk2);

        // prepare and send VA CHUNK responses with SERVICE_REQUEST_RAISED audio content and both input mode VOICE and DTMF
        WsEnvelopeVoiceVAResponse responseChunk3 = virtualAgentAdaptor.prepareAudioResponse(SERVICE_REQUEST_RAISED_AUDIO_B64, INPUT_VOICE_DTMF, ResponseType.CHUNK);
        virtualAgentService.sendMessage(session, responseChunk3);

        // prepare and send VA FINAL responses with EMPTY audio content and both input mode VOICE and DTMF
        WsEnvelopeVoiceVAResponse finalResponse = virtualAgentAdaptor.prepareAudioResponse(EMPTY_STRING, INPUT_VOICE_DTMF, ResponseType.FINAL);
        virtualAgentService.sendMessage(session, finalResponse);

        // create audio file of this chunk if writeAudioToFile is true
        if (writeAudioToFile) AudioFileUtil.writeWavWithMuLaw(currentUlawAudioBuffer);
    }

    private void sendStartOfInputEventIfNotSent(WebSocketSession session, AudioStreamState audioStreamState) throws IOException {
        if (!audioStreamState.isStartOfInputSent) {
            audioStreamState.isStartOfInputSent = true;
            log.info("Sending START_OF_INPUT event to client");
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.prepareVAResponse(START_OF_INPUT));
        }
    }

    private void sendEndOfInputEventIfNotSent(WebSocketSession session, AudioStreamState audioStreamState) throws IOException {
        if (audioStreamState.isStartOfInputSent) {
            audioStreamState.isStartOfInputSent = false;
            log.info("Sending END_OF_INPUT event to client");
            virtualAgentService.sendMessage(session, virtualAgentAdaptor.prepareVAResponse(END_OF_INPUT));
        }
    }

    private static class AudioStreamState {
        private ByteArrayOutputStream callerAudioChunkBuffer = new ByteArrayOutputStream();
        private ByteArrayOutputStream callerAudioBuffer = new ByteArrayOutputStream();
        private boolean isStartOfInputSent = false;

        private void resetChunkBuffer() {
            callerAudioChunkBuffer = new ByteArrayOutputStream();
        }

        private void resetAudioBuffer() {
            callerAudioBuffer = new ByteArrayOutputStream();
        }
    }
}
