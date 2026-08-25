package com.deployforge.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The permission matrix is security policy, so it is asserted rather than assumed. A regression here would
 * silently let a viewer deploy.
 */
class WorkspaceRoleTest {

    @Test
    @DisplayName("OWNER can do everything")
    void ownerHasEveryPermission() {
        for (Permission permission : Permission.values()) {
            assertThat(WorkspaceRole.OWNER.can(permission)).as(permission.name()).isTrue();
        }
    }

    @Test
    @DisplayName("ADMIN manages projects and configuration but not the workspace itself")
    void adminPermissions() {
        assertThat(WorkspaceRole.ADMIN.can(Permission.MANAGE_PROJECT)).isTrue();
        assertThat(WorkspaceRole.ADMIN.can(Permission.MANAGE_ENVIRONMENT)).isTrue();
        assertThat(WorkspaceRole.ADMIN.can(Permission.MANAGE_VARIABLES)).isTrue();
        assertThat(WorkspaceRole.ADMIN.can(Permission.DEPLOY)).isTrue();
        assertThat(WorkspaceRole.ADMIN.can(Permission.ROLLBACK)).isTrue();
        assertThat(WorkspaceRole.ADMIN.can(Permission.DELETE_PROJECT)).isTrue();

        assertThat(WorkspaceRole.ADMIN.can(Permission.MANAGE_WORKSPACE)).isFalse();
        assertThat(WorkspaceRole.ADMIN.can(Permission.DELETE_WORKSPACE)).isFalse();
        assertThat(WorkspaceRole.ADMIN.can(Permission.MANAGE_MEMBERS)).isFalse();
    }

    @Test
    @DisplayName("DEVELOPER ships code but cannot change configuration or roll back")
    void developerPermissions() {
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.DEPLOY)).isTrue();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.CANCEL_DEPLOYMENT)).isTrue();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.VIEW_LOGS)).isTrue();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.VIEW_MONITORING)).isTrue();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.USE_AI)).isTrue();

        assertThat(WorkspaceRole.DEVELOPER.can(Permission.ROLLBACK)).isFalse();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.MANAGE_VARIABLES)).isFalse();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.MANAGE_PROJECT)).isFalse();
        assertThat(WorkspaceRole.DEVELOPER.can(Permission.DELETE_PROJECT)).isFalse();
    }

    @Test
    @DisplayName("VIEWER is strictly read only and cannot spend AI quota")
    void viewerIsReadOnly() {
        assertThat(WorkspaceRole.VIEWER.can(Permission.VIEW_PROJECT)).isTrue();
        assertThat(WorkspaceRole.VIEWER.can(Permission.VIEW_LOGS)).isTrue();
        assertThat(WorkspaceRole.VIEWER.can(Permission.VIEW_MONITORING)).isTrue();
        assertThat(WorkspaceRole.VIEWER.can(Permission.VIEW_ANALYSIS)).isTrue();

        assertThat(WorkspaceRole.VIEWER.can(Permission.DEPLOY)).isFalse();
        assertThat(WorkspaceRole.VIEWER.can(Permission.CANCEL_DEPLOYMENT)).isFalse();
        assertThat(WorkspaceRole.VIEWER.can(Permission.USE_AI)).isFalse();
        assertThat(WorkspaceRole.VIEWER.can(Permission.MANAGE_VARIABLES)).isFalse();
    }

    @Test
    @DisplayName("permission sets grow monotonically with rank")
    void higherRolesIncludeLowerOnes() {
        assertThat(WorkspaceRole.DEVELOPER.permissions()).containsAll(WorkspaceRole.VIEWER.permissions());
        assertThat(WorkspaceRole.ADMIN.permissions()).containsAll(WorkspaceRole.DEVELOPER.permissions());
        assertThat(WorkspaceRole.OWNER.permissions()).containsAll(WorkspaceRole.ADMIN.permissions());
    }

    @Test
    @DisplayName("ranks order the roles for escalation checks")
    void ranksAreOrdered() {
        assertThat(WorkspaceRole.OWNER.rank()).isGreaterThan(WorkspaceRole.ADMIN.rank());
        assertThat(WorkspaceRole.ADMIN.rank()).isGreaterThan(WorkspaceRole.DEVELOPER.rank());
        assertThat(WorkspaceRole.DEVELOPER.rank()).isGreaterThan(WorkspaceRole.VIEWER.rank());
    }
}
