package com.cisco.wccai.ws.list;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListVARequest {

    @JsonProperty("customer_org_id")
    private String customerOrgId;

    @JsonProperty("is_default_virtual_agent_enabled")
    private Boolean isDefaultVirtualAgentEnabled;
}