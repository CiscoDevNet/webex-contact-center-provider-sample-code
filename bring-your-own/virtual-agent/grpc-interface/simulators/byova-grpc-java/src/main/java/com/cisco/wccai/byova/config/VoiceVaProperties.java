package com.cisco.wccai.byova.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the virtual agent's response behavior.
 *
 * <p>Configured with the {@code voice.va} prefix in {@code application.yml}. All nested records
 * expose the fine-grained knobs consumers typically tune when integrating the sample with their
 * own deployment.
 */
@ConfigurationProperties(prefix = "voice.va")
public record VoiceVaProperties(int inputTimeoutMillis, Audio audio, Dtmf dtmf) {

    public record Audio(
            boolean useChunkedAudio,
            boolean writeToFile,
            int amplitudeThreshold,
            int wavBufferSize,
            int chunkBufferSize,
            int ignoreBufferSize) {
    }

    public record Dtmf(int inputLength, int interDigitTimeoutMillis, int termChar) {
    }
}
