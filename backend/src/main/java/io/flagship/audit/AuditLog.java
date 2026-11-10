package io.flagship.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** Append-only record of who changed what, when, and the values before and after. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @Column(nullable = false, updatable = false, length = 40)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", updatable = false)
    private Map<String, Object> oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", updatable = false)
    private Map<String, Object> newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // required by JPA
    }

    public AuditLog(UUID organizationId, UUID actorId, String entityType, UUID entityId, String action,
                    Map<String, Object> oldValue, Map<String, Object> newValue) {
        this.organizationId = organizationId;
        this.actorId = actorId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public UUID getActorId() { return actorId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getAction() { return action; }
    public Map<String, Object> getOldValue() { return oldValue; }
    public Map<String, Object> getNewValue() { return newValue; }
    public Instant getCreatedAt() { return createdAt; }
}
