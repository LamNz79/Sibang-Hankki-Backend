# Clean Architecture — Phase 3

## Dependency direction

The backend is a modular monolith. Restaurant dependencies are now enforced as:

```text
adapter.in → application.port.in
application service/job → application.port.out + domain
adapter.out.persistence → application.port.out + domain/application read models
domain → Java only
```

```text
com.sibang.hankki
├── health/adapter/in/web
├── restaurant/domain/{booking,model}
├── restaurant/application/{booking,exception,model,port,prototype}
│   └── port/{in,out}
├── restaurant/adapter/in/{web,scheduling}
├── restaurant/adapter/out/persistence/{entity,repository}
└── user/{domain,application,adapter/out/persistence/entity}
```

## Phase 3 completed

Inbound adapters depend on use-case interfaces only. `RestaurantController` uses catalog and availability ports; `BookingSlotGenerationScheduler` uses the scheduled slot-generation port.

Application services use persistence-neutral outbound ports and read models. Catalog reads use typed restaurant, tag, business-hour, and gallery-count models. Booking availability and generation use typed booking settings plus domain slot/capacity types. `BookingDomainMapper` maps only application models to domain models.

Persistence adapters implement the catalog, booking-settings, and booking-slot ports. JPA entities, Spring Data repositories, JPQL, and `EntityManager` are confined to `adapter.out.persistence`. Gallery counts use a typed `RestaurantGalleryCount` record rather than an `Object[]` projection. Slot generation remains transactional in the application service; the persistence adapter maps generated domain slots to JPA entities when saving.

## Guardrails

ArchUnit retains all Phase 1 and Phase 2 rules and additionally prevents application code from depending on outbound adapters and inbound adapters from depending on concrete Spring `@Service` classes. This keeps adapters on port interfaces and keeps persistence implementation details outside the application layer.

## Compatibility and next work

Phase 3 preserves existing endpoints, successful JSON responses, exception-to-HTTP mapping, Asia/Ho_Chi_Minh behavior, scheduler behavior, GET availability's read-only behavior, schema, and Flyway V1–V6 migrations.

Clean Architecture refactoring is complete for the restaurant module. The next focused scope is the reservation MVP; it will add reservation use cases and ports without weakening these boundaries.
