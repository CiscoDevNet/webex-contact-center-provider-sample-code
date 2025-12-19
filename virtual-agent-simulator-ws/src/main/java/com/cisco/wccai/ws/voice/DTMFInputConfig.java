package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.DTMFDigits;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class DTMFInputConfig {
    @JsonProperty("inter_digit_timeout_msec")
    private Integer interDigitTimeoutMsec;

    @JsonProperty("termchar")
    private DTMFDigits termchar;

    @JsonProperty("dtmf_input_length")
    private Integer dtmfInputLength;
}
