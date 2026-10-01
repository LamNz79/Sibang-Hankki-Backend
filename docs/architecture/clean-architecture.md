# Clean Architecture — Phase 2

## Target package structure

The backend is a modular monolith. Restaurant dependencies flow from inbound adapters to application orchestration and then to pure domain rules; persistence remains an outbound adapter.

```text
com.sibang.hankki
├── health/adapter/in/web
├── restaurant/domain/{booking,model}
├── restaurant/application/{booking,exception,model,prototype}
├── restaurant/adapter/in/{web,scheduling}
├── restaurant/adapter/out/persistence/{entity,repository}
└── user/{domain,application,adapter/out/persistence/entity}
```

## Phase 2 completed

`restaurant.domain.booking` is pure Java. It contains typed booking policy, business-period, slot-capacity, and slot-candidate models plus rules for booking dates/windows, party-size limits, confirmation mode, capacity, and slot generation. The domain has JUnit-only tests and imports no Spring, HTTP, JPA, database, or repository types.

The application layer now loads JPA data, maps it to domain inputs, invokes domain rules, and maps results back to application responses or persistence entities. `RestaurantAvailabilityService` has no HTTP concern. It throws explicit application exceptions for invalid booking requests, missing restaurants, and missing booking settings.

`adapter.in.web` owns HTTP status mapping through `RestaurantExceptionHandler`: invalid requests map to 400, unknown restaurants to 404, and missing settings to 409. Spring MVC continues to reject missing or non-numeric query parameters with 400.

## Guardrails

ArchUnit protects domain isolation, inbound/application/persistence direction, the ban on inbound-to-outbound dependencies, application’s ban on Spring HTTP/web dependencies, and the placement of controller advice and exception handlers in inbound web adapters. Phase 1 controller, scheduler, entity, repository, reverse-web-dependency, and cycle rules remain active.

## Intentional Phase 3 debt

Application services still import JPA entities and Spring Data/JPA repositories. Phase 3 will introduce inbound use-case interfaces and outbound repository ports, with persistence adapters implementing those ports, so application code no longer depends on persistence implementations.

## Compatibility

Phase 2 preserves existing endpoints, successful JSON responses, Asia/Ho_Chi_Minh timezone behavior, scheduler behavior, availability’s read-only behavior, database schema, and Flyway V1–V6 migrations.
