package com.cisco.wccai.forking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for how the sample processes inbound forked audio.
 *
 * <p>Configured with the {@code forking} prefix in {@code application.yml}.
 *
 * @param logEveryNFrames how often progress for a given conversation should be logged. Set to a
 *                        large value (e.g. 50) to avoid one log line per audio frame on busy
 *                        streams; set to {@code 1} to log every frame.
 * @param writeToFile     when {@code true}, raw forked audio for each conversation/role is also
 *                        appended to a file under {@code captureDir}. Useful for offline
 *                        validation; should stay {@code false} in production.
 * @param captureDir      directory where captured audio files are written when
 *                        {@code writeToFile=true}. Created on demand.
 */
@ConfigurationProperties(prefix = "forking")
public record ForkingProperties(
        int logEveryNFrames,
        boolean writeToFile,
        String captureDir) {

    public ForkingProperties {
        if (logEveryNFrames <= 0) {
            logEveryNFrames = 50;
        }
        if (captureDir == null || captureDir.isBlank()) {
            captureDir = System.getProperty("user.home") + "/forked-audio";
        }
    }
}
