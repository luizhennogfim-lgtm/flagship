package io.flagship.flag;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * USER_LIST and GROUP rules only use "values". ATTRIBUTE rules also need "attribute" and "operator".
 * "serve" is the answer when the rule matches: true = feature ON, false = feature OFF.
 */
public record RuleRequest(
        @NotNull RuleType type,
        @Size(max = 80) String attribute,
        RuleOperator operator,
        @NotNull @Size(min = 1, max = 1000) List<@NotBlank @Size(max = 200) String> values,
        @NotNull Boolean serve,
        @Size(max = 200) String description) {
}
