# Customer saved restaurants

All endpoints require an authenticated `CUSTOMER` session. `PUT` and `DELETE` retain CSRF protection.
The customer ID always comes from the authenticated session.

## List favorites

```http
GET /api/customer/favorites
```

Returns a JSON array with the same lightweight objects as `GET /api/restaurants`, ordered by the newest
favorite first. Only restaurants that are currently `ACTIVE` and not archived are included.

## Save a restaurant

```http
PUT /api/customer/favorites/anan-saigon
X-CSRF-TOKEN: <token>
```

Returns `204 No Content`. Repeating the request is idempotent.

## Remove a restaurant

```http
DELETE /api/customer/favorites/anan-saigon
X-CSRF-TOKEN: <token>
```

Returns `204 No Content`. Removing an absent favorite is idempotent. An unknown, inactive, or archived
restaurant slug returns `404 Not Found`.
