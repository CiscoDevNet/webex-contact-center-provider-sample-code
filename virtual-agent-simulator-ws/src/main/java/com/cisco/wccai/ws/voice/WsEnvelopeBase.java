package com.cisco.wccai.ws.voice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Data
@SuperBuilder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class WsEnvelopeBase {
    @JsonProperty("type")
    private String type;

    @JsonProperty("seq")
    private Integer seq;

    @JsonProperty("ts")
    private String ts;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
