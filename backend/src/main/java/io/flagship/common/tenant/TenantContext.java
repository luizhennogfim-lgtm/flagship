package io.flagship.common.tenant;

import java.util.Optional;
import java.util.UUID;

/**
 * Holds the organization (tenant) of the request being processed.
 * It is filled by the security layer (Stage 2) and always cleared at the end of the request.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID organizationId) {
        CURRENT.set(organizationId);
    }

    public static Optional<UUID> get() {
        return Optional.ofNullable(CURRENT.get());
    }

    public static UUID require() {
        return get().orElseThrow(() -> new IllegalStateException("No tenant bound to the current request"));
    }

    public static void clear() {
        CURRENT.remove();
    }
}
