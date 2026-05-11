package com.cisco.wccai.ws.list;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for listing virtual agents
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListVAResponse {
    /**
     * List of bots for the selected provider.
     */
    @JsonProperty("virtual_agents")
    private List<VirtualAgentInfo> virtualAgents;
}
