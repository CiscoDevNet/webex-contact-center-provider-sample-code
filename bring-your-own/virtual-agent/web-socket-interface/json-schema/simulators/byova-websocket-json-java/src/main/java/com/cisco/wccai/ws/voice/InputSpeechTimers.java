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
public class InputSpeechTimers {
    @JsonProperty("max_speech_timeout_msec")
    private Integer maxSpeechTimeoutMsec;

    @JsonProperty("complete_timeout_msec")
    private Integer completeTimeoutMsec;

    @JsonProperty("incomplete_timeout_msec")
    private Integer incompleteTimeoutMsec;
}
