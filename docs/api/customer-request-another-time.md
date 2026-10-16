# Customer request another time

An active alternative proposal can be replaced with another persisted booking slot without creating
a second reservation.

## Guest reservation

```http
POST /api/customer/reservations/{reservationId}/proposals/{proposalId}/request-another-time
X-Reservation-Management-Token: <guest-management-token>
Content-Type: application/json

{
  "requestedDate": "2026-10-20",
  "requestedTime": "18:30"
}
```

## Customer account

```http
POST /api/customer/account/reservations/{reservationId}/proposals/{proposalId}/request-another-time
Content-Type: application/json
X-CSRF-TOKEN: <csrf-token>

{
  "requestedDate": "2026-10-20",
  "requestedTime": "18:30"
}
```

The account endpoint requires an active CUSTOMER session and normal CSRF protection. The guest
endpoint uses the reservation management token and follows the existing guest command CSRF policy.

The requested local date and time are interpreted in the restaurant timezone. The time must be in
the future, differ from the active proposal, and match a persisted slot with enough current capacity.
Capacity is checked only: returning the reservation to `PENDING` does not reserve or release seats.

On success, the existing reservation keeps its ID and customer data, moves to the requested slot,
and returns with `status: "PENDING"`, no visit status, and no active `alternativeProposal`. Repeating
the same completed request returns the current reservation without another event.
