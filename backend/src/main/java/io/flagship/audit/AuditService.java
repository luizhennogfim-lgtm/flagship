package io.flagship.audit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.common.api.PageResponse;
import io.flagship.common.api.Paging;
import io.flagship.common.tenant.TenantContext;
import io.flagship.security.AuthenticatedUser;
import io.flagship.security.CurrentUser;
import io.flagship.user.User;
import io.flagship.user.UserRepository;

@Service
public class AuditService {

    private final AuditLogRepository logs;
    private final UserRepository users;

    public AuditService(AuditLogRepository logs, UserRepository users) {
        this.logs = logs;
        this.users = users;
    }

    /** Joins the caller's transaction, so a change and its audit record are saved (or rolled back) together. */
    @Transactional
    public void record(String entityType, UUID entityId, AuditAction action,
                       Map<String, Object> oldValue, Map<String, Object> newValue) {
        AuthenticatedUser actor = CurrentUser.get();
        logs.save(new AuditLog(actor.organizationId(), actor.userId(), entityType, entityId,
                action.name(), oldValue, newValue));
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> list(UUID entityId, int page, int size) {
        UUID organizationId = TenantContext.require();
        Page<AuditLog> result = entityId == null
                ? logs.findByOrganizationIdOrderByCreatedAtDesc(organizationId, Paging.of(page, size))
                : logs.findByOrganizationIdAndEntityIdOrderByCreatedAtDesc(organizationId, entityId, Paging.of(page, size));

        List<UUID> actorIds = result.getContent().stream().map(AuditLog::getActorId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> emails = new HashMap<>();
        for (User user : users.findAllById(actorIds)) {
            emails.put(user.getId(), user.getEmail());
        }
        return PageResponse.from(result.map(log -> new AuditLogResponse(log.getId(), log.getActorId(),
                emails.get(log.getActorId()), log.getEntityType(), log.getEntityId(), log.getAction(),
                log.getOldValue(), log.getNewValue(), log.getCreatedAt())));
    }
}
