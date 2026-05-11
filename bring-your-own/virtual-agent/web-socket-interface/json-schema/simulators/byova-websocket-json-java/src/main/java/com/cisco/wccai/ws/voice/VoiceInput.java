package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.VoiceEncoding;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class VoiceInput {
    @JsonProperty("caller_audio_b64")
    private String callerAudioB64;

    @JsonProperty("encoding")
    private VoiceEncoding encoding;

    @JsonProperty("sample_rate_hertz")
    private Integer sampleRateHertz;

    @JsonProperty("audio_timestamp")
    private String audioTimestamp;

    @JsonProperty("language_code")
    private String languageCode;

    @JsonProperty("is_single_utterance")
    private Boolean isSingleUtterance;
}
