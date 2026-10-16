# Public restaurant menu

`GET /api/restaurants/{slug}` is public and includes the available menu for the selected `ACTIVE`,
non-archived restaurant. `GET /api/restaurants` remains a lightweight summary and does not include
or query menu data.

Response addition:

```json
{
  "menuCategories": [
    {
      "id": "00000000-0000-0000-0000-000000000001",
      "name": "Main dishes",
      "displayOrder": 0,
      "items": [
        {
          "id": "00000000-0000-0000-0000-000000000002",
          "name": "Grilled beef",
          "description": "Grilled beef with vegetables",
          "price": 250000,
          "currency": "VND",
          "imageUrl": "https://example.com/beef.jpg"
        }
      ]
    }
  ]
}
```

Only available, non-deleted items in non-deleted categories are returned. Categories without an
available item are omitted. Category order is `displayOrder`, then name, then ID; item ordering keeps
the existing deterministic owner-menu order. Menu data is always scoped to the selected restaurant.
