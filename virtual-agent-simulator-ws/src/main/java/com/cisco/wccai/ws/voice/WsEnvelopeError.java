package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.ErrorCode;
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
public class WsEnvelopeError extends WsEnvelopeBase {
    @JsonProperty("code")
    private ErrorCode code;

    @JsonProperty("status")
    private Integer status;

    @JsonProperty("detail")
    private String detail;
}
