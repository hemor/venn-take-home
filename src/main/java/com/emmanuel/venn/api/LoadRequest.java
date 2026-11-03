package com.emmanuel.venn.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LoadRequest(
        @JsonProperty("id") @NotBlank(message = "id is required") String id,
        @JsonProperty("customer_id") @NotBlank(message = "customer_id is required") String customerId,
        @JsonProperty("load_amount") @NotBlank(message = "load_amount is required") String loadAmount,
        @JsonProperty("time") @NotBlank(message = "time is required") String time) {}
