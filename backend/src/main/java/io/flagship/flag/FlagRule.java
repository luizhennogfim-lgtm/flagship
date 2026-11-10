package io.flagship.flag;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** One targeting rule. Rules of a flag state are checked in priority order; the first match decides. */
@Entity
@Table(name = "flag_rules")
public class FlagRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flag_config_id", nullable = false, updatable = false)
    private UUID flagConfigId;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 20)
    private RuleType type;

    @Column(length = 80)
    private String attribute;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RuleOperator operator;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "rule_values", nullable = false)
    private List<String> ruleValues;

    @Column(name = "serve_enabled", nullable = false)
    private boolean serveEnabled;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FlagRule() {
        // required by JPA
    }

    public FlagRule(UUID flagConfigId, int priority, RuleType type, String attribute, RuleOperator operator,
                    List<String> ruleValues, boolean serveEnabled, String description) {
        this.flagConfigId = flagConfigId;
        this.priority = priority;
        this.type = type;
        this.attribute = attribute;
        this.operator = operator;
        this.ruleValues = ruleValues;
        this.serveEnabled = serveEnabled;
        this.description = description;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getFlagConfigId() { return flagConfigId; }
    public int getPriority() { return priority; }
    public RuleType getType() { return type; }
    public String getAttribute() { return attribute; }
    public RuleOperator getOperator() { return operator; }
    public List<String> getRuleValues() { return ruleValues; }
    public boolean isServeEnabled() { return serveEnabled; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
