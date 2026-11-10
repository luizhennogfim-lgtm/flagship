package io.flagship.flag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** version must be the one the client last read; a different value means someone else changed it meanwhile. */
public record UpdateEnvironmentStateRequest(
        @NotNull Boolean enabled,
        @NotNull @Min(0) @Max(100) Integer rolloutPercentage,
        @NotNull Long version) {
}
