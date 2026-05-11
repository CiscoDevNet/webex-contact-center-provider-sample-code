package com.cisco.wccai.forking.service;

import com.cisco.wcc.ccai.media.v1.Conversationaudioforking.AudioStream;
import com.cisco.wcc.ccai.media.v1.Conversationaudioforking.ConversationAudioForkingRequest;
import com.cisco.wcc.ccai.media.v1.MediaServiceCommon.ParticipantRole;
import com.cisco.wccai.forking.config.ForkingProperties;
import com.google.protobuf.util.Timestamps;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Validates and (optionally) persists each forked audio frame received over the
 * Conversation Audio Forking gRPC stream.
 *
 * <p>The dialog-connector-simulator equivalent only logs an info line per request; this Spring
 * port adds two ergonomic improvements while keeping the same intent:
 * <ul>
 *   <li>Per-conversation/per-role frame counters and byte counters so that high-volume streams
 *       don't drown the logs.</li>
 *   <li>An opt-in raw-byte capture (via {@code forking.write-to-file=true}) for offline
 *       inspection of the audio. Files are appended atomically and namespaced by
 *       conversation id and participant role.</li>
 * </ul>
 *
 * <p>This service does <strong>not</strong> decode, transcode, or re-emit the audio — what your
 * own implementation does with the bytes is the entire point of the integration. Treat this
 * class as the place to plug in your downstream consumer (recording, ASR, analytics, ...).
 */
@Slf4j
@Service
public class ConversationAudioProcessor {

    private final ForkingProperties properties;
    private final ConcurrentMap<String, AtomicLong> frameCounters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> byteCounters = new ConcurrentHashMap<>();

    public ConversationAudioProcessor(ForkingProperties properties) {
        this.properties = properties;
    }

    /**
     * Process a single forked audio frame.
     *
     * @param request the inbound request — never {@code null}
     */
    public void process(ConversationAudioForkingRequest request) {
        AudioStream audio = request.getAudio();
        String conversationId = request.getConversationId();
        ParticipantRole role = audio.getRole();
        String key = conversationId + "/" + role.name();

        long framesSoFar = frameCounters
                .computeIfAbsent(key, k -> new AtomicLong())
                .incrementAndGet();
        long totalBytes = byteCounters
                .computeIfAbsent(key, k -> new AtomicLong())
                .addAndGet(audio.getAudioData().size());

        if (framesSoFar == 1 || framesSoFar % properties.logEveryNFrames() == 0) {
            long latencyMillis = audio.hasAudioTimestamp()
                    ? System.currentTimeMillis() - Timestamps.toMillis(audio.getAudioTimestamp())
                    : -1;
            log.info(
                    "Forked audio: conversationId={}, orgId={}, role={}, encoding={}, sampleRateHz={}, "
                            + "frames={}, totalBytes={}, frameLatencyMs={}",
                    conversationId,
                    request.getCustomerOrgId(),
                    role,
                    audio.getEncoding(),
                    audio.getSampleRateHertz(),
                    framesSoFar,
                    totalBytes,
                    latencyMillis);
        }

        if (properties.writeToFile() && audio.getAudioData().size() > 0) {
            appendAudio(conversationId, role, audio.getAudioData().toByteArray());
        }
    }

    /** Releases per-conversation counters; called when the client half-closes the stream. */
    public void onConversationCompleted(String conversationId) {
        frameCounters.keySet().removeIf(k -> k.startsWith(conversationId + "/"));
        byteCounters.keySet().removeIf(k -> k.startsWith(conversationId + "/"));
        log.info("Released counters for conversationId={}", conversationId);
    }

    private void appendAudio(String conversationId, ParticipantRole role, byte[] data) {
        try {
            Path dir = Paths.get(properties.captureDir());
            Files.createDirectories(dir);
            Path file = dir.resolve(safeFileName(conversationId) + "-" + role.name().toLowerCase() + ".raw");
            Files.write(file, data, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Failed to append forked audio for conversationId={}: {}", conversationId, e.getMessage());
        }
    }

    /** Strip path separators / unsafe chars from a conversation id before using it as a filename. */
    private static String safeFileName(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "unknown";
        }
        return conversationId.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
