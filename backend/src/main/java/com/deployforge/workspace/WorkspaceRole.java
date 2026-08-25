package com.deployforge.workspace;

import java.util.EnumSet;
import java.util.Set;

/**
 * Workspace level roles and the permissions they grant.
 *
 * <p>The mapping lives here, server side, and every check goes through
 * {@link AuthorizationService}. The frontend receives the caller's role purely so it can hide
 * controls - it is never the enforcement point.
 */
public enum WorkspaceRole {

    /** Full control, including deleting the workspace. */
    OWNER,

    /** Manages projects, environments, variables and deployments. Cannot alter the workspace itself. */
    ADMIN,

    /** Ships code: deploy, redeploy, cancel, read everything. Cannot change configuration. */
    DEVELOPER,

    /** Read only. */
    VIEWER;

    private static final Set<Permission> READ_ONLY =
            EnumSet.of(
                    Permission.VIEW_PROJECT,
                    Permission.VIEW_LOGS,
                    Permission.VIEW_MONITORING,
                    Permission.VIEW_ANALYSIS);

    private static final Set<Permission> DEVELOPER_PERMISSIONS;
    private static final Set<Permission> ADMIN_PERMISSIONS;

    static {
        DEVELOPER_PERMISSIONS = EnumSet.copyOf(READ_ONLY);
        DEVELOPER_PERMISSIONS.addAll(
                EnumSet.of(Permission.DEPLOY, Permission.CANCEL_DEPLOYMENT, Permission.USE_AI));

        ADMIN_PERMISSIONS = EnumSet.copyOf(DEVELOPER_PERMISSIONS);
        ADMIN_PERMISSIONS.addAll(
                EnumSet.of(
                        Permission.ROLLBACK,
                        Permission.MANAGE_PROJECT,
                        Permission.DELETE_PROJECT,
                        Permission.MANAGE_ENVIRONMENT,
                        Permission.MANAGE_VARIABLES));
    }

    public boolean can(Permission permission) {
        return switch (this) {
            case OWNER -> true;
            case ADMIN -> ADMIN_PERMISSIONS.contains(permission);
            case DEVELOPER -> DEVELOPER_PERMISSIONS.contains(permission);
            case VIEWER -> READ_ONLY.contains(permission);
        };
    }

    public Set<Permission> permissions() {
        return switch (this) {
            case OWNER -> EnumSet.allOf(Permission.class);
            case ADMIN -> EnumSet.copyOf(ADMIN_PERMISSIONS);
            case DEVELOPER -> EnumSet.copyOf(DEVELOPER_PERMISSIONS);
            case VIEWER -> EnumSet.copyOf(READ_ONLY);
        };
    }

    /** Ranking used to prevent privilege escalation when changing another member's role. */
    public int rank() {
        return switch (this) {
            case OWNER -> 4;
            case ADMIN -> 3;
            case DEVELOPER -> 2;
            case VIEWER -> 1;
        };
    }
}
