package com.cisco.wccai.ws.voice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class InputHandlingConfig {
    @JsonProperty("dtmf_config")
    private DTMFInputConfig dtmfConfig;

    @JsonProperty("speech_timers")
    private InputSpeechTimers speechTimers;
}
