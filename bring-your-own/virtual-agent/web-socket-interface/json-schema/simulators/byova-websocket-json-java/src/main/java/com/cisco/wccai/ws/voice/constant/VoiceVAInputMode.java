package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum VoiceVAInputMode {
    @JsonProperty("INPUT_VOICE_MODE_UNSPECIFIED")
    INPUT_VOICE_MODE_UNSPECIFIED,

    @JsonProperty("INPUT_VOICE")
    INPUT_VOICE,

    @JsonProperty("INPUT_EVENT_DTMF")
    INPUT_EVENT_DTMF,

    @JsonProperty("INPUT_VOICE_DTMF")
    INPUT_VOICE_DTMF
}
