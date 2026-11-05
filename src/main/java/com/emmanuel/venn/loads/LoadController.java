package com.emmanuel.venn.loads;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/loads")
class LoadController {

    private final LoadHandler handler;

    LoadController(LoadHandler handler) {
        this.handler = handler;
    }

    @Operation(summary = "Accept or decline a fund-load attempt",
            description = "A load id already seen for the customer is ignored and returns no body.")
    @ApiResponse(responseCode = "200", description = "Decision for the load attempt")
    @ApiResponse(responseCode = "204", description = "Load id already seen for this customer, ignored",
            content = @Content)
    @ApiResponse(responseCode = "400", description = "Malformed or invalid request",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "Request body larger than any valid load",
            content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping
    ResponseEntity<LoadResponse> submit(@RequestBody LoadRequest request) {
        return handler.handle(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
