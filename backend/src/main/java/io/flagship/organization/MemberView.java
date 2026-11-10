package io.flagship.organization;

import java.util.UUID;

/** A member of the current organization, as exposed by the API. */
public record MemberView(UUID userId, String email, String fullName, Role role) {
}
