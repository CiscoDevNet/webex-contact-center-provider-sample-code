package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ResponseType {
    @JsonProperty("FINAL")
    FINAL,

    @JsonProperty("PARTIAL")
    PARTIAL,

    @JsonProperty("CHUNK")
    CHUNK
}
