package io.flagship.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.flagship.organization.Role;
import io.jsonwebtoken.JwtException;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private final JwtService service = new JwtService(new JwtProperties(SECRET, 60));

    @Test
    void tokenRoundTripKeepsUserOrganizationAndRole() {
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        AuthenticatedUser parsed = service.parse(service.issue(userId, orgId, Role.EDITOR));

        assertThat(parsed.userId()).isEqualTo(userId);
        assertThat(parsed.organizationId()).isEqualTo(orgId);
        assertThat(parsed.role()).isEqualTo(Role.EDITOR);
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = service.issue(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);
        int position = token.lastIndexOf('.') + 10;
        char original = token.charAt(position);
        String tampered = token.substring(0, position) + (original == 'a' ? 'b' : 'a') + token.substring(position + 1);

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(new JwtProperties("ffffffffffffffffffffffffffffffff", 60));
        String token = other.issue(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, -1));
        String token = shortLived.issue(UUID.randomUUID(), UUID.randomUUID(), Role.OWNER);

        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties("too-short", 60)))
                .isInstanceOf(IllegalStateException.class);
    }
}
