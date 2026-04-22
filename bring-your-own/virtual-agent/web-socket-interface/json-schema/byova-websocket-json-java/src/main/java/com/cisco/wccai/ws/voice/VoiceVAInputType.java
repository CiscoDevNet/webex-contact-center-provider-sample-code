package com.cisco.wccai.ws.voice;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(value = VoiceInputWrapper.class, name = "audio_input"),
        @JsonSubTypes.Type(value = DTMFInputsWrapper.class, name = "dtmf_input"),
        @JsonSubTypes.Type(value = EventInputWrapper.class, name = "event_input")
})
public interface VoiceVAInputType {
}
