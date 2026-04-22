package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum VoiceEncoding {
    @JsonProperty("UNSPECIFIED_FORMAT")
    UNSPECIFIED_FORMAT,

    @JsonProperty("LINEAR16_FORMAT")
    LINEAR16_FORMAT,

    @JsonProperty("MULAW_FORMAT")
    MULAW_FORMAT,

    @JsonProperty("ALAW_FORMAT")
    ALAW_FORMAT
}
