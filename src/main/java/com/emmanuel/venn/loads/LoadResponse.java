package com.emmanuel.venn.loads;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LoadResponse(
        @JsonProperty("id") String id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("accepted") boolean accepted) {}
