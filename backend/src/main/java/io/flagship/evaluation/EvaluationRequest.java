package io.flagship.evaluation;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EvaluationRequest(
        @NotBlank @Size(max = 100) String flagKey,
        @NotBlank @Size(max = 200) String userId,
        @NotBlank @Size(max = 40) String environment,
        @Size(max = 100) List<@NotBlank @Size(max = 100) String> groups,
        @Size(max = 50) Map<String, String> attributes) {
}
