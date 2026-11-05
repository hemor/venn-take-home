package com.emmanuel.venn.loads;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record LoadRequest(
        @JsonProperty("id")
        @NotBlank(message = "id is required")
        @Size(max = MAX_ID_LENGTH, message = "id must be at most " + MAX_ID_LENGTH + " characters")
        @Pattern(regexp = ID_CHARACTERS, message = "id may only contain letters, digits, '.', '_' and '-'")
        String id,
        @JsonProperty("customer_id")
        @NotBlank(message = "customer_id is required")
        @Size(max = MAX_ID_LENGTH, message = "customer_id must be at most " + MAX_ID_LENGTH + " characters")
        @Pattern(regexp = ID_CHARACTERS, message = "customer_id may only contain letters, digits, '.', '_' and '-'")
        String customerId,
        @JsonProperty("load_amount") @NotBlank(message = "load_amount is required") String loadAmount,
        @JsonProperty("time") @NotBlank(message = "time is required") String time) {

    public static final int MAX_ID_LENGTH = 64;
    static final String ID_CHARACTERS = "[A-Za-z0-9._-]*";

    public LoadRequest trimmed() {
        return new LoadRequest(strip(id), strip(customerId), strip(loadAmount), strip(time));
    }

    private static String strip(String value) {
        return value == null ? null : value.strip();
    }
}
