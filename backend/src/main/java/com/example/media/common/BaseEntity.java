package com.example.media.common;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id
    protected UUID id;
    @Column(nullable = false, updatable = false)
    protected Instant createdAt;
    @Column(nullable = false)
    protected Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID();
        createdAt = updatedAt = Instant.now();
    }
    @PreUpdate
    protected void onUpdate() { updatedAt = Instant.now(); }
    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
