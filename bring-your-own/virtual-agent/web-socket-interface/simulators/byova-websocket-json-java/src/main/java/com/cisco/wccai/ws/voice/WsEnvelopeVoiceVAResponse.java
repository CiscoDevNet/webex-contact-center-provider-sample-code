package com.cisco.wccai.ws.voice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class WsEnvelopeVoiceVAResponse extends WsEnvelopeBase {
    @JsonProperty("payload")
    private VoiceVAResponse payload;
}
