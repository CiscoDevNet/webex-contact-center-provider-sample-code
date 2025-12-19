package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.OutputEventType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class OutputEvent {
    @JsonProperty("event_type")
    private OutputEventType eventType;

    @JsonProperty("name")
    private String name;

    @JsonProperty("metadata")
    private Map<String, Object> metadata;
}
