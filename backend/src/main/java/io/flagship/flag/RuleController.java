package io.flagship.flag;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/flags/{id}/environments/{environmentKey}/rules")
public class RuleController {

    private final RuleService service;

    public RuleController(RuleService service) {
        this.service = service;
    }

    @GetMapping
    public RulesResponse get(@PathVariable UUID id, @PathVariable String environmentKey) {
        return service.get(id, environmentKey);
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','EDITOR')")
    public RulesResponse replace(@PathVariable UUID id, @PathVariable String environmentKey,
                                 @Valid @RequestBody ReplaceRulesRequest request) {
        return service.replace(id, environmentKey, request);
    }
}
