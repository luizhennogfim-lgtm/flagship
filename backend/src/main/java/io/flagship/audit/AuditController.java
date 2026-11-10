package io.flagship.audit;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.flagship.common.api.PageResponse;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    /** Newest first. Pass entityId (for example a flag id) to see the history of one item. */
    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    public PageResponse<AuditLogResponse> list(@RequestParam(required = false) UUID entityId,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return service.list(entityId, page, size);
    }
}
