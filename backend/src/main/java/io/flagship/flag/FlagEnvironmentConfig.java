package io.flagship.flag;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * State of one flag in one environment. The version column provides optimistic locking:
 * if two people save the same version, only the first one wins.
 */
@Entity
@Table(name = "flag_environment_configs")
public class FlagEnvironmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_id", nullable = false, updatable = false)
    private UUID flagId;

    @Column(name = "environment_id", nullable = false, updatable = false)
    private UUID environmentId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "rollout_percentage", nullable = false)
    private int rolloutPercentage;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FlagEnvironmentConfig() {
        // required by JPA
    }

    /** New configs start OFF with 0% rollout. */
    public FlagEnvironmentConfig(UUID flagId, UUID environmentId) {
        this.flagId = flagId;
        this.environmentId = environmentId;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }

    public void update(boolean enabled, int rolloutPercentage, UUID updatedBy) {
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.updatedBy = updatedBy;
    }

    /** Marks the state as changed (for example when its rules change) so the version is incremented. */
    public void markUpdated(UUID updatedBy) {
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFlagId() { return flagId; }
    public UUID getEnvironmentId() { return environmentId; }
    public boolean isEnabled() { return enabled; }
    public int getRolloutPercentage() { return rolloutPercentage; }
    public long getVersion() { return version; }
    public UUID getUpdatedBy() { return updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}
