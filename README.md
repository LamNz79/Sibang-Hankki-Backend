# Sibang-Hankki-Backend
# Sibang-Hankki-Backend

## CI

Pull requests targeting `main` run through GitHub Actions. CI uses Java 17 and a temporary PostgreSQL 17 instance.

Equivalent local command:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

## Public restaurant location filters

The public restaurant list remains backward compatible and accepts optional Vietnamese administrative
location codes:

```http
GET /api/restaurants
GET /api/restaurants?provinceCode=79
GET /api/restaurants?provinceCode=79&wardCode=27169
GET /api/restaurants?wardCode=27169
```

Both codes must be positive integers. Filters are applied in PostgreSQL and only `ACTIVE`, non-archived
restaurants are returned. An unmatched filter returns `200 OK` with `[]`; the response remains the existing
lightweight `RestaurantSummaryResponse` array. Flyway V15 backfills the V3 prototype restaurants from their
existing `city_slug`; it leaves ward codes unset rather than guessing them from legacy district text.

Public catalog presentation comes only from persisted restaurant profiles, tags, business hours, images,
and menu data. Until reviews are implemented, `rating` and `ratingCount` are `0`; legacy availability fields
are `null`, `slotMatrix` is empty, and clients should use the date/party-size availability endpoint for live slots.

## System-admin restaurant review lifecycle

An ADMIN changes restaurant approval state with:

```http
PATCH /api/admin/restaurants/{restaurantId}/status
Content-Type: application/json
X-CSRF-TOKEN: <token>

{"status":"PENDING"}
```

`PENDING` means the restaurant has been submitted and is awaiting system-admin review. Supported transitions are:

- `DRAFT -> PENDING`
- `CHANGES_REQUESTED -> PENDING`
- `PENDING -> ACTIVE`
- `PENDING -> CHANGES_REQUESTED`
- `ACTIVE -> SUSPENDED`
- `SUSPENDED -> ACTIVE`

Sending the current status again is idempotent when that status is accepted by the endpoint. Every other transition is rejected with HTTP 409; invalid or missing status values return HTTP 400. The endpoint is ADMIN-only and PATCH requests require CSRF protection.

## System-admin reservation read API

System administrators can read reservations across restaurants with these ADMIN-only endpoints:

```http
GET /api/admin/reservations?page=0&size=20&query=Minh&restaurantId=<uuid>&status=CONFIRMED&dateFrom=2026-10-01&dateTo=2026-10-31
GET /api/admin/reservations/{reservationId}
```

The list accepts page sizes `10`, `20`, `50`, or `100`. Search covers the reservation reference,
customer name/phone/email, and restaurant name/slug. Supported status filters are `PENDING`,
`CONFIRMED`, `CHECKED_IN`, `COMPLETED`, `CANCELLED`, `DECLINED`, and `NO_SHOW`.
`CHECKED_IN` keeps the owner-list semantics and includes `ARRIVED`, `SEATED`, and `COMPLETED`;
`COMPLETED` is also available when the report needs only completed visits.

`dateFrom` and `dateTo` are inclusive local dates applied to `startsAt`. Global reports use
`app.reporting.time-zone`, which defaults to `Asia/Ho_Chi_Minh` and can be configured with
`APP_REPORTING_TIME_ZONE`. Results are ordered by `startsAt DESC`, then `id DESC`.

Example page response:

```json
{
  "items": [
    {
      "id": "70000000-0000-0000-0000-000000000001",
      "reference": "SHK-ABC123",
      "restaurantId": "00000000-0000-0000-0000-000000000002",
      "restaurantName": "The Royal Pavilion",
      "restaurantSlug": "royal-pavilion",
      "customerName": "Minh Lam",
      "customerEmail": "minh@example.com",
      "customerPhone": "0900000000",
      "startsAt": "2026-10-05T11:30:00Z",
      "endsAt": "2026-10-05T13:00:00Z",
      "partySize": 2,
      "status": "CONFIRMED",
      "visitStatus": "EXPECTED",
      "createdAt": "2026-10-01T00:00:00Z",
      "updatedAt": "2026-10-01T00:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Both endpoints are read-only: they do not require CSRF and expose no reservation command. OWNER and
STAFF APIs remain separately restaurant-scoped and cannot use these global queries.

The detail response includes the latest `alternativeProposal` when present. Its `initiator` is
`RESTAURANT` for an owner-created alternative or `CUSTOMER` for a customer reschedule request.

## Password management

Every authenticated role can change its own password. The request remains CSRF-protected and the
successful caller session is invalidated immediately:

```http
PATCH /api/auth/password
Content-Type: application/json
X-CSRF-TOKEN: <token>

{"currentPassword":"OldPassword@2026!","newPassword":"NewPassword@2026!"}
```

System administrators can reset a non-ADMIN user's password with `PATCH
/api/admin/users/{userId}/password` and body `{"newPassword":"TemporaryPassword@2026!"}`. Existing
sessions become unauthorized on their next request. Both endpoints return `204 No Content` and never
return or log plaintext passwords or password hashes. ADMIN accounts must use the self-service endpoint.

## Reservation persistence foundation

Flyway V7 introduces the `reservations` and append-only `reservation_events` tables. It enforces canonical reservation statuses (`PENDING`, `ALTERNATIVE_PROPOSED`, `CONFIRMED`, `DECLINED`, `EXPIRED`, `CANCELLED`) and visit statuses (`EXPECTED`, `ARRIVED`, `SEATED`, `COMPLETED`, `NO_SHOW`), referential integrity, idempotency keys, event command IDs, optimistic-lock versions, and query indexes.

Guest reservations are supported at the persistence boundary: `customer_id` is nullable while customer name and phone remain mandatory. Financial data, alternative proposals, APIs, state/capacity transitions, and check-in workflows are intentionally excluded. The next reservation PR will add create-reservation plus atomic capacity handling for overlapping slots.
