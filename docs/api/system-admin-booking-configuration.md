# System-admin booking configuration API

All endpoints below require an authenticated `ADMIN`. Read operations do not require a CSRF token; `PUT` and
`POST` operations do. The restaurant is selected only by the `restaurantId` path value. Owner endpoints keep using
the restaurant ID from the owner session.

## Booking settings

```http
GET /api/admin/restaurants/{restaurantId}/booking-settings
```

```http
PUT /api/admin/restaurants/{restaurantId}/booking-settings
Content-Type: application/json
X-CSRF-TOKEN: <token>

{
  "confirmationMode": "HYBRID",
  "manualConfirmationMinPartySize": 7,
  "guestCapacity": 20,
  "slotIntervalMinutes": 60,
  "diningDurationMinutes": 90,
  "bookingWindowDays": 30,
  "minimumPartySize": 1,
  "maximumOnlinePartySize": 10,
  "largePartyThreshold": 11,
  "cancellationCutoffMinutes": 120,
  "noShowGraceMinutes": 15
}
```

The response uses the same shape. Nullable values remain nullable and an explicit `null` replaces the current value;
zero is retained where the existing booking policy permits it, including cancellation cutoff and no-show grace.
Validation is shared with the owner settings flow. The first `PUT` also initializes the settings row for a restaurant
that does not have one yet; internal checkout-hold and pending-expiry values use the established prototype defaults
of 10 and 30 minutes because those internal fields are not part of either settings API.

## Business hours

```http
GET /api/admin/restaurants/{restaurantId}/business-hours
```

```http
PUT /api/admin/restaurants/{restaurantId}/business-hours
Content-Type: application/json
X-CSRF-TOKEN: <token>

{
  "hours": [
    { "dayOfWeek": 1, "opensAt": "11:30", "closesAt": "14:00" },
    { "dayOfWeek": 1, "opensAt": "17:30", "closesAt": "22:00" }
  ]
}
```

Days use ISO values `1` (Monday) through `7` (Sunday). Each period must open before it closes and periods on the
same day cannot overlap. Saving hours uses the existing owner application service and regenerates the selected
restaurant's slots in the configured booking window.

## Regenerate booking slots

```http
POST /api/admin/restaurants/{restaurantId}/booking-slots/regenerate
X-CSRF-TOKEN: <token>
```

Example response:

```json
{
  "deletedSlots": 12,
  "generatedSlots": 48
}
```

Regeneration delegates to the existing slot-regeneration service. It only affects the selected restaurant, keeps
slots referenced by reservations or proposals, preserves occupied capacity, and uses that restaurant's booking
settings, business hours, and configured timezone.

An unknown or archived restaurant returns `404`. Invalid settings or hours return `400`; missing booking settings
return `409`.
