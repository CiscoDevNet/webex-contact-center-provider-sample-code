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
public class TextContent {
    @JsonProperty("text")
    private String text;

    @JsonProperty("ssml")
    private String ssml;

    @JsonProperty("language_code")
    private String languageCode;
}
