package com.cisco.wccai.byova.service;

import com.cisco.wccai.byova.audio.MuLawCodec;
import org.springframework.stereotype.Component;

/** Stateless helper that reports whether an audio chunk is below a configured amplitude. */
@Component
public class SilenceDetector {

    /**
     * Returns {@code true} when every decoded PCM sample is below the supplied threshold.
     *
     * @param muLawAudio µ-law-encoded audio bytes; {@code null} / empty is treated as silence
     * @param amplitudeThreshold absolute amplitude above which a sample is considered speech
     */
    public boolean isSilence(byte[] muLawAudio, int amplitudeThreshold) {
        if (muLawAudio == null || muLawAudio.length == 0) {
            return true;
        }
        short[] pcm = MuLawCodec.muLawToLinear(muLawAudio);
        for (short sample : pcm) {
            if (Math.abs((int) sample) > amplitudeThreshold) {
                return false;
            }
        }
        return true;
    }
}
