package com.emmanuel.venn.api;

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

    @PostMapping
    ResponseEntity<LoadResponse> submit(@RequestBody LoadRequest request) {
        return handler.handle(request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
