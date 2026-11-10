package io.flagship.flag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The key never changes after creation, because applications reference the flag by it. */
public record UpdateFlagRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 2000) String description) {
}
