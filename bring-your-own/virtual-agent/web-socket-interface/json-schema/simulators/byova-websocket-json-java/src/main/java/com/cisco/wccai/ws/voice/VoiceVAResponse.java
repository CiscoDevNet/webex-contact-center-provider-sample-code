package com.cisco.wccai.ws.voice;

import com.cisco.wccai.ws.voice.constant.ResponseType;
import com.cisco.wccai.ws.voice.constant.VoiceVAInputMode;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder(setterPrefix = "set")
@NoArgsConstructor
@AllArgsConstructor
public class VoiceVAResponse {
    @JsonProperty("prompts")
    private List<Prompt> prompts;

    @JsonProperty("output_events")
    private List<OutputEvent> outputEvents;

    @JsonProperty("input_sensitive")
    private Boolean inputSensitive;

    @JsonProperty("input_mode")
    private VoiceVAInputMode inputMode;

    @JsonProperty("input_handling_config")
    private InputHandlingConfig inputHandlingConfig;

    @JsonProperty("session_transcript")
    private TextContent sessionTranscript;

    @JsonProperty("session_summary")
    private TextContent sessionSummary;

    @JsonProperty("response_type")
    private ResponseType responseType;
}
