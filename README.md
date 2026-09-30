# Sibang-Hankki-Backend
# Sibang-Hankki-Backend

## CI

Pull requests targeting `main` run through GitHub Actions. CI uses Java 17 and a temporary PostgreSQL 17 instance.

Equivalent local command:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```
