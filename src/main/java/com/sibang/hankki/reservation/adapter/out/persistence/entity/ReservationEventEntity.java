package com.sibang.hankki.reservation.adapter.out.persistence.entity;

import com.sibang.hankki.reservation.domain.model.ReservationEvent;
import com.sibang.hankki.reservation.domain.model.ReservationEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservation_events")
public class ReservationEventEntity {

    @Id
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ReservationEventType eventType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "command_id", unique = true, length = 255)
    private String commandId;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReservationEventEntity() {
    }

    public ReservationEventEntity(ReservationEvent event) {
        this.id = event.id();
        this.reservationId = event.reservationId();
        this.eventType = event.eventType();
        this.actorUserId = event.actorUserId();
        this.commandId = event.commandId();
        this.requestFingerprint = event.requestFingerprint();
        this.metadata = event.metadata();
        this.createdAt = event.createdAt() == null ? Instant.now() : event.createdAt();
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public ReservationEventType getEventType() {
        return eventType;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public String getMetadata() {
        return metadata;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
