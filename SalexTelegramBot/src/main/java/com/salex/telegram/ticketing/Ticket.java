package com.salex.telegram.ticketing;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain model representing a support ticket flow.
 * All write operations should go through the service/repository layers.
 */
public final class Ticket {
    private final long id;
    private final TicketStatus status;
    private final TicketPriority priority;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final long createdBy;
    private final Long assignee;
    private final String summary;
    private final String details;

    private Ticket(Builder builder) {
        this.id = builder.id;
        this.status = builder.status;
        this.priority = builder.priority;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.createdBy = builder.createdBy;
        this.assignee = builder.assignee;
        this.summary = builder.summary;
        this.details = builder.details;
    }

    public long getId() {
        return id;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public TicketPriority getPriority() {
        return priority;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getCreatedBy() {
        return createdBy;
    }

    public Long getAssignee() {
        return assignee;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetails() {
        return details;
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private long id;
        private TicketStatus status;
        private TicketPriority priority;
        private Instant createdAt;
        private Instant updatedAt;
        private long createdBy;
        private Long assignee;
        private String summary;
        private String details;

        private Builder() {
        }

        private Builder(Ticket source) {
            this.id = source.id;
            this.status = source.status;
            this.priority = source.priority;
            this.createdAt = source.createdAt;
            this.updatedAt = source.updatedAt;
            this.createdBy = source.createdBy;
            this.assignee = source.assignee;
            this.summary = source.summary;
            this.details = source.details;
        }

        public Builder id(long id) {
            this.id = id;
            return this;
        }

        public Builder status(TicketStatus status) {
            this.status = status;
            return this;
        }

        public Builder priority(TicketPriority priority) {
            this.priority = priority;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder createdBy(long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder assignee(Long assignee) {
            this.assignee = assignee;
            return this;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder details(String details) {
            this.details = details;
            return this;
        }

        public Ticket build() {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(priority, "priority");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(details, "details");
            return new Ticket(this);
        }
    }
}
