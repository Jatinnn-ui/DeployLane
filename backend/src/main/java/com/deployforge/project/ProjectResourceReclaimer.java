package com.deployforge.project;

import java.util.UUID;

/**
 * Port used when a project is deleted or archived, so infrastructure belonging to it is released
 * before its database rows disappear.
 *
 * <p>Implemented by the deployment module (which owns containers, images and build directories).
 * Declaring it here keeps the dependency pointing in one direction: deployment knows about projects,
 * projects only know about this interface.
 */
public interface ProjectResourceReclaimer {

    /** Stops and removes every container, image and build directory owned by the project. */
    void reclaim(UUID projectId);
}
