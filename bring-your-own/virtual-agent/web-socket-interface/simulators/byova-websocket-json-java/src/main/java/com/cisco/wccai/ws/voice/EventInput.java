package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.EventInputType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventInput {
    @JsonProperty("event_type")
    private EventInputType eventType;

    @JsonProperty("name")
    private String name;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;
}
