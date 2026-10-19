# Customer reservation rescheduling

Customers can request a different persisted booking slot without changing party size.
All dates and times are interpreted in the reservation restaurant's timezone.

## Customer requests

Guest reservation:

```http
POST /api/customer/reservations/{reservationId}/change-requests
X-Reservation-Management-Token: <management-token>
Content-Type: application/json

{
  "requestedDate": "2026-10-20",
  "requestedTime": "18:30",
  "message": "Optional message"
}
```

Account reservation (authenticated CUSTOMER session, CSRF required):

```http
POST /api/customer/account/reservations/{reservationId}/change-requests
Content-Type: application/json
X-CSRF-TOKEN: <csrf-token>

{
  "requestedDate": "2026-10-20",
  "requestedTime": "18:30",
  "message": "Optional message"
}
```

For a `PENDING` reservation, the existing reservation is moved directly to the
requested slot and remains `PENDING`; capacity is not reserved. For a
`CONFIRMED` + `EXPECTED` reservation, the original schedule and capacity remain
unchanged and a customer-initiated pending proposal is returned.

The existing `alternativeProposal` response remains compatible and now also
contains `initiator`, whose value is `RESTAURANT` or `CUSTOMER`.

## Restaurant response

Authenticated OWNER/STAFF sessions may accept or decline a customer request:

```http
POST /api/owner/reservations/{reservationId}/change-requests/{requestId}/accept
X-CSRF-TOKEN: <csrf-token>
```

```http
POST /api/owner/reservations/{reservationId}/change-requests/{requestId}/decline
X-CSRF-TOKEN: <csrf-token>
```

Accepting atomically reserves the new slot, releases the old slot, keeps the
reservation `CONFIRMED` + `EXPECTED`, and records `RESCHEDULED`. Declining keeps
the original reservation and capacity and records `RESCHEDULE_DECLINED`.
Cancellation closes any still-pending proposal so it cannot be accepted later.
