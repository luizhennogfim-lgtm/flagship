package io.flagship.environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateEnvironmentRequest(
        @NotBlank @Size(min = 2, max = 40)
        @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "use lower-case letters, digits and single hyphens")
        String key,
        @NotBlank @Size(max = 80) String name) {
}
