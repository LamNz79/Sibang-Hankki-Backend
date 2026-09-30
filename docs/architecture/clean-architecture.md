# Clean Architecture — Phase 1

## Target package structure

The backend is evolving as a modular monolith. Each feature follows an inbound adapter → application → domain direction. Persistence is an outbound adapter.

```text
com.sibang.hankki
├── health/adapter/in/web
├── restaurant/domain/model
├── restaurant/application/{booking,exception,model,prototype}
├── restaurant/adapter/in/{web,scheduling}
├── restaurant/adapter/out/persistence/{entity,repository}
└── user/{domain,application,adapter/out/persistence/entity}
```

## Responsibilities and dependency direction

- `adapter.in.web` owns HTTP controllers and translates HTTP requests/responses.
- `adapter.in.scheduling` owns scheduled and application-ready triggers.
- `application` coordinates use cases. In Phase 1 it still reads persistence repositories directly.
- `domain` contains framework-independent business concepts. It must not depend on application or adapters.
- `adapter.out.persistence.entity` contains JPA entities; `adapter.out.persistence.repository` contains Spring Data and JPA repositories.

ArchUnit guards these boundaries: domain isolation, no application dependency on inbound adapters, no persistence dependency on web, no reverse dependency into web controllers, adapter placement for controllers/schedulers/entities/repositories, and no restaurant package cycle.

## Phase 1 limitation and intentional debt

This phase is package/file refactoring plus guardrails only. It preserves the existing API, JSON, scheduling behavior, database schema, and Flyway V1–V6 migrations. The application layer still depends directly on JPA entities and repositories, and `RestaurantAvailabilityService` still uses `ResponseStatusException`. These are intentionally not hidden by ArchUnit exclusions.

The move requires public visibility for application services invoked by web/scheduling adapters, persistence repositories and entities consumed across the new package boundaries, their required constructors/accessors, application response models/prototype data, and booking-generation job/time-zone constant. This is mechanical visibility for the current modular-monolith boundaries; it is not a public HTTP API change.

## Roadmap

### Phase 2

Extract pure-Java booking domain logic with unit tests that use neither Spring nor a database. Replace `ResponseStatusException` in application/domain code with application errors; map those errors to HTTP in the web adapter.

### Phase 3

Define inbound use-case interfaces and outbound repository ports. Implement the ports in persistence adapters so application code no longer imports JPA entities, Spring Data repositories, or persistence implementation details.
