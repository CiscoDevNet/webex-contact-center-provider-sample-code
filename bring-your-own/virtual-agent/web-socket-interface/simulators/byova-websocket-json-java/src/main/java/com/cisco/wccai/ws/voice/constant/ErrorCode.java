package com.cisco.wccai.ws.voice.constant;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ErrorCode {
    @JsonProperty("unauthorized")
    UNAUTHORIZED,

    @JsonProperty("bad_request")
    BAD_REQUEST,

    @JsonProperty("unsupported_media")
    UNSUPPORTED_MEDIA,

    @JsonProperty("upstream_error")
    UPSTREAM_ERROR,

    @JsonProperty("rate_limit")
    RATE_LIMIT,

    @JsonProperty("timeout")
    TIMEOUT
}
