package com.cisco.wccai.service;

import com.cisco.wccai.util.AudioFormatUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SpeechDetectionService {

    public boolean isSilence(byte[] audioData, int amplitudeThreshold) {
        return isSilence(AudioFormatUtil.muLawToLinear(audioData), amplitudeThreshold);
    }

    public boolean isSilence(short[] pcmData, int amplitudeThreshold) {
        if (pcmData == null) {
            return true; // Empty data is silence
        }
        for (short sample : pcmData) {
            if (Math.abs(sample) > amplitudeThreshold) {
                return false; // Found a sample above threshold
            }
        }
        return true; // All samples are below threshold
    }


}
