package io.campuscore.restfulapi.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Temporary authenticated contract probe for the single-app migration shell. */
@RestController
@RequestMapping("/api/v1/contract")
public class ContractController {

    @GetMapping
    public Map<String, Object> contract() {
        return Map.of(
                "apiVersion", "v1",
                "application", "restful-api",
                "status", "shell",
                "migration", "not-cut-over");
    }

    @PostMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(@Valid @RequestBody PingRequest request) {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "echo", request.message(),
                "writer", "restful-api-shell"));
    }

    public record PingRequest(@NotBlank(message = "message is required") String message) {
    }
}
