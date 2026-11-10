package io.flagship.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import io.flagship.AbstractIntegrationTest;

/** Applies the Flyway migrations on a real PostgreSQL and lets Hibernate validate the mappings. */
class DatabaseMigrationIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OrganizationRepository organizations;

    @Test
    void flywayCreatesAllCoreTables() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);

        assertThat(tables).contains("organizations", "users", "teams", "memberships",
                "environments", "feature_flags", "flag_environment_configs", "audit_logs");
    }

    @Test
    void organizationCanBeSavedAndFoundBySlug() {
        Organization saved = organizations.save(new Organization("Acme Inc", "acme-migration-test"));

        Organization found = organizations.findBySlug("acme-migration-test").orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getCreatedAt()).isNotNull();
    }
}
