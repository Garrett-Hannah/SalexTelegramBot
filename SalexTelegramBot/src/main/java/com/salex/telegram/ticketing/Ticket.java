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

    /**
     * Returns the ticket identifier.
     *
     * @return unique ticket id
     */
    public long getId() {
        return id;
    }

    /**
     * Returns the current lifecycle status.
     *
     * @return ticket status
     */
    public TicketStatus getStatus() {
        return status;
    }

    /**
     * Returns the priority assigned to the ticket.
     *
     * @return ticket priority
     */
    public TicketPriority getPriority() {
        return priority;
    }

    /**
     * Returns the timestamp when the ticket was created.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns the timestamp when the ticket was last modified.
     *
     * @return update timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Returns the identifier of the user who created the ticket.
     *
     * @return creator id
     */
    public long getCreatedBy() {
        return createdBy;
    }

    /**
     * Returns the user assigned to the ticket, if any.
     *
     * @return assignee id or {@code null} when unassigned
     */
    public Long getAssignee() {
        return assignee;
    }

    /**
     * Returns the ticket summary text.
     *
     * @return short description
     */
    public String getSummary() {
        return summary;
    }

    /**
     * Returns the detailed ticket description.
     *
     * @return detailed description
     */
    public String getDetails() {
        return details;
    }

    /**
     * Produces a builder pre-populated with the current ticket values.
     *
     * @return builder seeded with this ticket's data
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /**
     * Creates a new blank builder for constructing tickets.
     *
     * @return empty ticket builder
     */
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

        /**
         * Sets the ticket identifier.
         *
         * @param id ticket id
         * @return builder instance
         */
        public Builder id(long id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the ticket status.
         *
         * @param status ticket status
         * @return builder instance
         */
        public Builder status(TicketStatus status) {
            this.status = status;
            return this;
        }

        /**
         * Sets the ticket priority.
         *
         * @param priority ticket priority
         * @return builder instance
         */
        public Builder priority(TicketPriority priority) {
            this.priority = priority;
            return this;
        }

        /**
         * Sets the ticket creation timestamp.
         *
         * @param createdAt creation instant
         * @return builder instance
         */
        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Sets the ticket last updated timestamp.
         *
         * @param updatedAt update instant
         * @return builder instance
         */
        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        /**
         * Sets the ID of the user who created the ticket.
         *
         * @param createdBy creator user id
         * @return builder instance
         */
        public Builder createdBy(long createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        /**
         * Sets the ID of the user assigned to the ticket.
         *
         * @param assignee assignee user id
         * @return builder instance
         */
        public Builder assignee(Long assignee) {
            this.assignee = assignee;
            return this;
        }

        /**
         * Sets the ticket summary.
         *
         * @param summary brief description
         * @return builder instance
         */
        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        /**
         * Sets the ticket details.
         *
         * @param details detailed description
         * @return builder instance
         */
        public Builder details(String details) {
            this.details = details;
            return this;
        }

        /**
         * Builds an immutable ticket instance, validating mandatory fields.
         *
         * @return constructed ticket
         * @throws NullPointerException if required properties are missing
         */
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
