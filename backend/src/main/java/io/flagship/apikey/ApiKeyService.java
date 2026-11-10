package io.flagship.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.flagship.audit.AuditAction;
import io.flagship.audit.AuditService;
import io.flagship.common.exception.ResourceNotFoundException;
import io.flagship.common.tenant.TenantContext;
import io.flagship.security.AuthenticatedUser;
import io.flagship.security.CurrentUser;

@Service
public class ApiKeyService {

    private static final String PREFIX = "fs_";
    private static final int PREFIX_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository keys;
    private final AuditService audit;

    public ApiKeyService(ApiKeyRepository keys, AuditService audit) {
        this.keys = keys;
        this.audit = audit;
    }

    @Transactional
    public CreatedApiKeyResponse create(CreateApiKeyRequest request) {
        AuthenticatedUser actor = CurrentUser.get();
        String rawKey = PREFIX + randomToken();
        ApiKey key = keys.save(new ApiKey(actor.organizationId(), request.name().trim(),
                rawKey.substring(0, PREFIX_LENGTH), sha256(rawKey), actor.userId()));
        audit.record("API_KEY", key.getId(), AuditAction.API_KEY_CREATED, null, describe(key));
        return new CreatedApiKeyResponse(key.getId(), key.getName(), key.getKeyPrefix(), rawKey, key.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        return keys.findByOrganizationIdOrderByCreatedAtDesc(TenantContext.require()).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public void revoke(UUID id) {
        ApiKey key = keys.findByIdAndOrganizationId(id, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("API key", id));
        if (key.isRevoked()) {
            return;
        }
        key.revoke();
        audit.record("API_KEY", key.getId(), AuditAction.API_KEY_REVOKED, describe(key), null);
    }

    /** Returns the key if it exists and is not revoked. Called on every evaluation request. */
    @Transactional(readOnly = true)
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return keys.findByKeyHashAndRevokedAtIsNull(sha256(rawKey.trim()));
    }

    private static Map<String, Object> describe(ApiKey key) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", key.getName());
        snapshot.put("prefix", key.getKeyPrefix());
        return snapshot;
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required by the JVM specification", ex);
        }
    }
}
