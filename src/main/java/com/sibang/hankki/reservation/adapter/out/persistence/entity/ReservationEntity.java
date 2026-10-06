package com.sibang.hankki.reservation.adapter.out.persistence.entity;

import com.sibang.hankki.reservation.domain.model.Reservation;
import com.sibang.hankki.reservation.domain.model.ReservationStatus;
import com.sibang.hankki.reservation.domain.model.VisitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "reservations")
public class ReservationEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 32)
    private String reference;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "restaurant_id", nullable = false)
    private UUID restaurantId;

    @Column(name = "booking_slot_id")
    private UUID bookingSlotId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "customer_name", nullable = false, length = 120)
    private String customerName;

    @Column(name = "customer_email", length = 320)
    private String customerEmail;

    @Column(name = "customer_phone", nullable = false, length = 30)
    private String customerPhone;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "capacity_override", nullable = false)
    private boolean capacityOverride;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_status", length = 30)
    private VisitStatus visitStatus;

    @Column(name = "special_request", columnDefinition = "text")
    private String specialRequest;

    @Column(name = "pre_order_note", columnDefinition = "text")
    private String preOrderNote;

    @Column(name = "management_token_hash", length = 64)
    private String managementTokenHash;

    @Column(name = "check_in_token_hash", unique = true, length = 255)
    private String checkInTokenHash;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_in_by")
    private UUID checkedInBy;

    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservationEntity() {
    }

    public ReservationEntity(Reservation reservation) {
        this.id = reservation.id();
        this.version = reservation.version();
        this.createdAt = reservation.createdAt();
        this.updatedAt = reservation.updatedAt();
        updateFrom(reservation);
    }

    public void updateFrom(Reservation reservation) {
        this.reference = reservation.reference();
        this.idempotencyKey = reservation.idempotencyKey();
        this.requestFingerprint = reservation.requestFingerprint();
        this.restaurantId = reservation.restaurantId();
        this.bookingSlotId = reservation.bookingSlotId();
        this.customerId = reservation.customerId();
        this.customerName = reservation.customerName();
        this.customerEmail = reservation.customerEmail();
        this.customerPhone = reservation.customerPhone();
        this.startsAt = reservation.startsAt();
        this.endsAt = reservation.endsAt();
        this.partySize = reservation.partySize();
        this.status = reservation.status();
        this.capacityOverride = reservation.capacityOverride();
        this.visitStatus = reservation.visitStatus();
        this.specialRequest = reservation.specialRequest();
        this.preOrderNote = reservation.preOrderNote();
        this.managementTokenHash = reservation.managementTokenHash();
        this.checkInTokenHash = reservation.checkInTokenHash();
        this.checkedInAt = reservation.checkedInAt();
        this.checkedInBy = reservation.checkedInBy();
    }

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public UUID getRestaurantId() {
        return restaurantId;
    }

    public UUID getBookingSlotId() {
        return bookingSlotId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public int getPartySize() {
        return partySize;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public boolean isCapacityOverride() {
        return capacityOverride;
    }

    public VisitStatus getVisitStatus() {
        return visitStatus;
    }

    public String getSpecialRequest() {
        return specialRequest;
    }

    public String getPreOrderNote() {
        return preOrderNote;
    }

    public String getManagementTokenHash() {
        return managementTokenHash;
    }

    public String getCheckInTokenHash() {
        return checkInTokenHash;
    }

    public Instant getCheckedInAt() {
        return checkedInAt;
    }

    public UUID getCheckedInBy() {
        return checkedInBy;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
