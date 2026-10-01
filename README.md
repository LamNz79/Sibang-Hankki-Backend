# Sibang-Hankki-Backend
# Sibang-Hankki-Backend

## CI

Pull requests targeting `main` run through GitHub Actions. CI uses Java 17 and a temporary PostgreSQL 17 instance.

Equivalent local command:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

## Reservation persistence foundation

Flyway V7 introduces the `reservations` and append-only `reservation_events` tables. It enforces canonical reservation statuses (`PENDING`, `ALTERNATIVE_PROPOSED`, `CONFIRMED`, `DECLINED`, `EXPIRED`, `CANCELLED`) and visit statuses (`EXPECTED`, `ARRIVED`, `SEATED`, `COMPLETED`, `NO_SHOW`), referential integrity, idempotency keys, event command IDs, optimistic-lock versions, and query indexes.

Guest reservations are supported at the persistence boundary: `customer_id` is nullable while customer name and phone remain mandatory. Financial data, alternative proposals, APIs, state/capacity transitions, and check-in workflows are intentionally excluded. The next reservation PR will add create-reservation plus atomic capacity handling for overlapping slots.
