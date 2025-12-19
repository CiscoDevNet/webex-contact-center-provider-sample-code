package com.cisco.wccai.ws.voice;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class Prompt {
    @JsonProperty("text")
    private String text;

    @JsonProperty("audio_uri")
    private String audioUri;

    @JsonProperty("audio_content_b64")
    private String audioContentB64;

    @JsonProperty("is_barge_in_enabled")
    private Boolean isBargeInEnabled;
}
