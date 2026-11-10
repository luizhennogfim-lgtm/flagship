package io.flagship.auth;

import jakarta.validation.constraints.NotBlank;

/** organizationSlug is only needed when the user belongs to more than one organization. */
public record LoginRequest(
        @NotBlank String email,
        @NotBlank String password,
        String organizationSlug) {
}
