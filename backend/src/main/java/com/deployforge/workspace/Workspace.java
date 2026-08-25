package com.deployforge.workspace;

import com.deployforge.common.jpa.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** A tenant boundary: projects, members and deployments all belong to exactly one workspace. */
@Entity
@Table(name = "workspaces")
public class Workspace extends AuditedEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 120)
    private String slug;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    protected Workspace() {}

    public Workspace(String name, String slug, UUID ownerId) {
        this.name = name;
        this.slug = slug;
        this.ownerId = ownerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(UUID ownerId) {
        this.ownerId = ownerId;
    }
}
