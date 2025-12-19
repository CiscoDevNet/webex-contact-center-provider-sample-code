package com.cisco.wccai.ws.voice;

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
public class VoiceVARequest {
    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("customer_org_id")
    private String customerOrgId;

    @JsonProperty("virtual_agent_id")
    private String virtualAgentId;

    @JsonProperty("allow_partial_responses")
    private Boolean allowPartialResponses;

    @JsonProperty("vendor_specific_config")
    private String vendorSpecificConfig;

    @JsonProperty("voice_va_input_type")
    private VoiceVAInputType voiceVaInputType;

    @JsonProperty("additional_info")
    private Map<String, String> additionalInfo;
}
