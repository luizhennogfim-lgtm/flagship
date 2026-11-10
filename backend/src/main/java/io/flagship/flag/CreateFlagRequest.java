package io.flagship.flag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateFlagRequest(
        @NotBlank @Size(min = 2, max = 100)
        @Pattern(regexp = "^[a-z0-9]+(?:[-_.][a-z0-9]+)*$",
                message = "use lower-case letters, digits and the separators - _ .")
        String key,
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description) {
}
