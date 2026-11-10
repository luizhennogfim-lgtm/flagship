package io.flagship.organization;

/** Roles inside an organization (RBAC). */
public enum Role {
    OWNER,   // everything, including granting OWNER
    ADMIN,   // manage members, teams and flags
    EDITOR,  // create and change flags
    VIEWER   // read-only
}
