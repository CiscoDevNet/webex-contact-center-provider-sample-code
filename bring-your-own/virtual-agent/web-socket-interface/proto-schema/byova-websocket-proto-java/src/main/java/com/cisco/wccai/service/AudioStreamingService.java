package com.cisco.wccai.service;

import com.cisco.wcc.ccai.media.v1.Voicevirtualagent;
import com.cisco.wccai.util.AudioFileUtil;
import com.google.protobuf.ByteString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.END_OF_INPUT;
import static com.cisco.wcc.ccai.media.v1.ByovaCommon.OutputEvent.EventType.START_OF_INPUT;
import static com.cisco.wcc.ccai.media.v1.Voicevirtualagent.VoiceVAInputMode.INPUT_VOICE_DTMF;
import static com.cisco.wccai.common.AudioConstant.SERVICE_REQUEST_RAISED;
import static com.cisco.wccai.common.AudioConstant.WAIT_FOR_SERVICE_REQUEST;

/**
 * Accumulates caller audio bytes arriving on the WebSocket stream, runs basic silence detection,
 * and emits {@link Voicevirtualagent.VoiceVAResponse} protobuf messages (wrapped in
 * {@code START_OF_INPUT}/{@code END_OF_INPUT} markers) once an utterance boundary is detected.
 */
@Slf4j
@Service
public class AudioStreamingService {
    private final SpeechDetectionService speechDetectionService;
    private final VirtualAgentService virtualAgentService;
    private final VirtualAgentAdaptor virtualAgentAdaptor;

    private static final ByteString WAIT_FOR_SERVICE_REQUEST_AUDIO = AudioFileUtil.audioContentFromResources(WAIT_FOR_SERVICE_REQUEST);
    private static final ByteString SERVICE_REQUEST_RAISED_AUDIO = AudioFileUtil.audioContentFromResources(SERVICE_REQUEST_RAISED);
    private static final ByteString MU_LAW_WAV_HEADER = ByteString.copyFrom(AudioFileUtil.getMuLawWavHeader());

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

    public AudioStreamingService(SpeechDetectionService speechDetectionService,
                                 VirtualAgentService virtualAgentService,
                                 VirtualAgentAdaptor virtualAgentAdaptor) {
        this.speechDetectionService = speechDetectionService;
        this.virtualAgentService = virtualAgentService;
        this.virtualAgentAdaptor = virtualAgentAdaptor;
    }

    public void processAudioStream(Voicevirtualagent.VoiceVARequest request, WebSocketSession session) throws IOException {
        AudioStreamState audioStreamState = audioStreamStates.computeIfAbsent(session.getId(), key -> new AudioStreamState());
        byte[] callerAudioBytes = request.getAudioInput().getCallerAudio().toByteArray();

        // ignoreBufferSize is to ignore very small audio packets that may be just noise,
        // using a silence/speech detection engine is recommended in production.
        if (callerAudioBytes.length <= ignoreBufferSize) {
            log.debug("Ignoring audio less than {} bytes", ignoreBufferSize);
            return;
        }

        try {
            audioStreamState.callerAudioChunkBuffer.write(callerAudioBytes);

            int bufferSizeToProcess = useChunkedAudio ? chunkBufferSizeToProcess : wavBufferSizeToProcess;
            if (audioStreamState.callerAudioChunkBuffer.size() < bufferSizeToProcess) {
                return;
            }

            byte[] currentChunkBuffer = audioStreamState.callerAudioChunkBuffer.toByteArray();
            audioStreamState.resetChunkBuffer();

            if (speechDetectionService.isSilence(currentChunkBuffer, amplitudeThreshold)) {
                // If the current large chunk is all silence AND there is previous audio collected
                processBufferedAudio(session, audioStreamState);
            } else {
                sendStartOfInputEventIfNotSent(session, audioStreamState);
                log.info("Caller is speaking. Adding chunks to the buffer");
                audioStreamState.callerAudioBuffer.write(currentChunkBuffer);
                log.info("Total buffered audio size is now {} bytes after adding chunk size {} bytes",
                        audioStreamState.callerAudioBuffer.size(), currentChunkBuffer.length);
            }
        } catch (Exception e) {
            log.error("Error processing audio stream. Was it chunking flow: {}", useChunkedAudio, e);
        }
    }

    public void removeSessionState(String sessionId) {
        audioStreamStates.remove(sessionId);
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

            audioStreamState.resetAudioBuffer();
        } else {
            log.info("Silence detected, but no buffered audio to process.");
        }
    }

    // Non-chunking flow: a single FINAL response is sent with the WAV header prepended to the data.
    private void processWavAudio(WebSocketSession session, byte[] bufferedAudio) throws IOException {
        ByteString wavAudioContent = MU_LAW_WAV_HEADER.concat(ByteString.copyFrom(bufferedAudio));
        Voicevirtualagent.VoiceVAResponse vaResponse = virtualAgentAdaptor.prepareAudioResponse(
                wavAudioContent, INPUT_VOICE_DTMF, Voicevirtualagent.VoiceVAResponse.ResponseType.FINAL);
        virtualAgentService.sendMessage(session, vaResponse);

        if (writeAudioToFile) AudioFileUtil.writeWavWithMuLaw(bufferedAudio);
    }

    // Chunking flow: multiple CHUNK responses followed by a FINAL response, with raw µ-law audio.
    private void processChunkAudio(WebSocketSession session, byte[] currentUlawAudioBuffer) throws IOException {
        ByteString callerChunk = ByteString.copyFrom(currentUlawAudioBuffer);

        Voicevirtualagent.VoiceVAResponse responseChunk1 = virtualAgentAdaptor.prepareAudioResponse(
                callerChunk, INPUT_VOICE_DTMF, Voicevirtualagent.VoiceVAResponse.ResponseType.CHUNK);
        virtualAgentService.sendMessage(session, responseChunk1);

        Voicevirtualagent.VoiceVAResponse responseChunk2 = virtualAgentAdaptor.prepareAudioResponse(
                WAIT_FOR_SERVICE_REQUEST_AUDIO, INPUT_VOICE_DTMF, Voicevirtualagent.VoiceVAResponse.ResponseType.CHUNK);
        virtualAgentService.sendMessage(session, responseChunk2);

        Voicevirtualagent.VoiceVAResponse responseChunk3 = virtualAgentAdaptor.prepareAudioResponse(
                SERVICE_REQUEST_RAISED_AUDIO, INPUT_VOICE_DTMF, Voicevirtualagent.VoiceVAResponse.ResponseType.CHUNK);
        virtualAgentService.sendMessage(session, responseChunk3);

        Voicevirtualagent.VoiceVAResponse finalResponse = virtualAgentAdaptor.prepareAudioResponse(
                ByteString.EMPTY, INPUT_VOICE_DTMF, Voicevirtualagent.VoiceVAResponse.ResponseType.FINAL);
        virtualAgentService.sendMessage(session, finalResponse);

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
