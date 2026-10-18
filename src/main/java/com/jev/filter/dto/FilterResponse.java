package com.jev.filter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FilterResponse(
        @JsonProperty("should_show") boolean shouldShow,
        String category,
        @JsonProperty("doubt_probability") Double doubtProbability) {
}
