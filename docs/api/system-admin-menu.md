# System Admin menu API

These endpoints let an authenticated `ADMIN` manage the menu of one explicitly selected, non-archived restaurant.
They work for every restaurant approval status, including `DRAFT`, `PENDING`, `CHANGES_REQUESTED`, `ACTIVE`, and
`SUSPENDED`. Owner and staff menu endpoints remain scoped to the restaurant in their session and are unchanged.

## Endpoints

- `GET /api/admin/restaurants/{restaurantId}/menu`
- `POST /api/admin/restaurants/{restaurantId}/menu/categories`
- `PUT /api/admin/restaurants/{restaurantId}/menu/categories/{categoryId}`
- `DELETE /api/admin/restaurants/{restaurantId}/menu/categories/{categoryId}`
- `POST /api/admin/restaurants/{restaurantId}/menu/items`
- `PUT /api/admin/restaurants/{restaurantId}/menu/items/{itemId}`
- `DELETE /api/admin/restaurants/{restaurantId}/menu/items/{itemId}`

All endpoints require the `ADMIN` role. POST, PUT, and DELETE requests also require a CSRF token. The authenticated
admin user ID is stored in the existing menu audit columns.

## Examples

Create a category:

```json
{
  "name": "Main dishes",
  "displayOrder": 1
}
```

Create or update an item:

```json
{
  "categoryId": "00000000-0000-0000-0000-000000000001",
  "name": "Grilled beef",
  "description": "Grilled beef with vegetables",
  "price": 250000,
  "currency": "VND",
  "imageUrl": "https://example.com/image.jpg",
  "available": true
}
```

Menu responses keep the owner API contract: `categories` contain `id`, `name`, and `displayOrder`; `items` contain
`id`, `categoryId`, `name`, `description`, `price`, `currency`, `imageUrl`, and `available`.

An unknown or archived restaurant returns `404`. Category and item IDs are restaurant-scoped; using a resource or
category from another restaurant also returns `404`. Existing menu validation returns `400` for invalid input.
