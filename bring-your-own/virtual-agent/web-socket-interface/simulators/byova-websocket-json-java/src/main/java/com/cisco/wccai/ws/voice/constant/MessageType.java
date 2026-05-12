package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MessageType {
    @JsonProperty("VOICE_VA_REQUEST")
    VOICE_VA_REQUEST,

    @JsonProperty("VOICE_VA_RESPONSE")
    VOICE_VA_RESPONSE,

    @JsonProperty("ERROR")
    ERROR,

    @JsonProperty("PING")
    PING,

    @JsonProperty("PONG")
    PONG
}
