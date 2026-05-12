package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum OutputEventType {
    @JsonProperty("UNSPECIFIED_EVENT")
    UNSPECIFIED_EVENT,

    @JsonProperty("SESSION_END")
    SESSION_END,

    @JsonProperty("TRANSFER_TO_AGENT")
    TRANSFER_TO_AGENT,

    @JsonProperty("CUSTOM_EVENT")
    CUSTOM_EVENT,

    @JsonProperty("START_OF_INPUT")
    START_OF_INPUT,

    @JsonProperty("END_OF_INPUT")
    END_OF_INPUT,

    @JsonProperty("NO_MATCH")
    NO_MATCH,

    @JsonProperty("NO_INPUT")
    NO_INPUT
}
