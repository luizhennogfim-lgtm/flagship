package io.flagship.flag;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Replaces the whole ordered rule list at once. The first rule has the highest priority. */
public record ReplaceRulesRequest(
        @NotNull Long version,
        @NotNull @Size(max = 50) List<@Valid RuleRequest> rules) {
}
