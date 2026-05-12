package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum EventInputType {
    @JsonProperty("UNSPECIFIED_INPUT")
    UNSPECIFIED_INPUT,

    @JsonProperty("SESSION_START")
    SESSION_START,

    @JsonProperty("SESSION_END")
    SESSION_END,

    @JsonProperty("NO_INPUT")
    NO_INPUT,

    @JsonProperty("START_OF_DTMF")
    START_OF_DTMF,

    @JsonProperty("CUSTOM_EVENT")
    CUSTOM_EVENT
}
