package io.flagship.environment;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** A deployment environment of an organization (development, staging, production, ...). */
@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "key", nullable = false, updatable = false, length = 40)
    private String envKey;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Environment() {
        // required by JPA
    }

    public Environment(UUID organizationId, String envKey, String name) {
        this(organizationId, envKey, name, null);
    }

    /** The explicit creation time lets the default environments keep a stable order. */
    public Environment(UUID organizationId, String envKey, String name, Instant createdAt) {
        this.organizationId = organizationId;
        this.envKey = envKey;
        this.name = name;
        this.createdAt = createdAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getOrganizationId() { return organizationId; }
    public String getEnvKey() { return envKey; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
