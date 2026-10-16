# System Admin restaurant media API

These endpoints let an authenticated `ADMIN` manage the image gallery of one explicitly selected,
non-archived restaurant. They work for `DRAFT`, `PENDING`, `CHANGES_REQUESTED`, `ACTIVE`, and
`SUSPENDED` restaurants. Existing owner media endpoints remain unchanged.

## Endpoints

- `GET /api/admin/restaurants/{restaurantId}/media`
- `POST /api/admin/restaurants/{restaurantId}/media`
- `PUT /api/admin/restaurants/{restaurantId}/media/{mediaId}`
- `DELETE /api/admin/restaurants/{restaurantId}/media/{mediaId}`

All endpoints require the `ADMIN` role. POST, PUT, and DELETE requests also require a CSRF token.

Create or update request:

```json
{
  "imageUrl": "https://example.com/restaurant.jpg",
  "altText": "Restaurant dining room",
  "sortOrder": 0
}
```

Response:

```json
{
  "images": [
    {
      "id": "00000000-0000-0000-0000-000000000001",
      "imageUrl": "https://example.com/restaurant.jpg",
      "altText": "Restaurant dining room",
      "sortOrder": 0
    }
  ]
}
```

`imageUrl` must be an HTTP or HTTPS URL and at most 2000 characters. `altText` is optional and at
most 300 characters. `sortOrder` must be zero or greater. Missing or archived restaurants and media
belonging to another restaurant return `404`; invalid requests return `400`.

This API stores image URLs only. It does not upload, process, sign, or host image files.
