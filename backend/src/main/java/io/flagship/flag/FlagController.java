package io.flagship.flag;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.flagship.common.api.PageResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/flags")
public class FlagController {

    private static final String CAN_EDIT = "hasAnyRole('OWNER','ADMIN','EDITOR')";
    private static final String CAN_ADMIN = "hasAnyRole('OWNER','ADMIN')";

    private final FlagService service;

    public FlagController(FlagService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_EDIT)
    public FlagResponse create(@Valid @RequestBody CreateFlagRequest request) {
        return service.create(request);
    }

    /**
     * Filters: search (key or name), archived, and state in one environment (environment + enabled together).
     */
    @GetMapping
    public PageResponse<FlagResponse> list(@RequestParam(required = false) String search,
                                           @RequestParam(required = false) String environment,
                                           @RequestParam(required = false) Boolean enabled,
                                           @RequestParam(defaultValue = "false") boolean archived,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return service.list(search, environment, enabled, archived, page, size);
    }

    @GetMapping("/{id}")
    public FlagResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize(CAN_EDIT)
    public FlagResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateFlagRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize(CAN_ADMIN)
    public void archive(@PathVariable UUID id) {
        service.archive(id);
    }

    @PutMapping("/{id}/environments/{environmentKey}")
    @PreAuthorize(CAN_EDIT)
    public EnvironmentState updateEnvironment(@PathVariable UUID id, @PathVariable String environmentKey,
                                              @Valid @RequestBody UpdateEnvironmentStateRequest request) {
        return service.updateEnvironment(id, environmentKey, request);
    }
}
