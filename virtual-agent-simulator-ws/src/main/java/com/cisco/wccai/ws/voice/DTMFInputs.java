package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.DTMFDigits;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DTMFInputs {
    @JsonProperty("dtmf_events")
    private List<DTMFDigits> dtmfEvents;
}
